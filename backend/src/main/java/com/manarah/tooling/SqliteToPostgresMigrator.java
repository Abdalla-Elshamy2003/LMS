package com.manarah.tooling;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One-time data copy from the legacy SQLite database into the PostgreSQL database this app now
 * runs on. Only active on the 'migrate' profile (see application-migrate.yml, which also forces
 * spring.main.web-application-type=none so this runs and exits instead of starting the server).
 *
 * <p>Run against the 'postgres' profile's own datasource (so it targets whatever
 * MANARAH_DB_URL/USERNAME/PASSWORD point at) plus manarah.migrate.sqlite-path pointing at the
 * SQLite file to read from, e.g.:
 * <pre>
 * java -jar manarah-lms.jar --spring.profiles.active=postgres,migrate \
 *      --manarah.migrate.sqlite-path=/path/to/manarah.db
 * </pre>
 *
 * <p>Safe to re-run: any table that already has rows in the Postgres target is skipped, so a
 * failed run can be retried without duplicating already-copied tables. Foreign key and trigger
 * enforcement is disabled for the duration of the load (session_replication_role=replica) so
 * tables can be copied in any order, then identity sequences are resynced to MAX(id)+1 per table
 * so new rows created after the cutover don't collide with migrated ids.
 *
 * <p>This is a thin, intentionally un-clever ETL: it works purely at the JDBC/ResultSetMetaData
 * level (no Hibernate/JPA involved) so every column - including ones added by later migrations -
 * is copied generically without needing an entity mapping.
 */
@Component
@Profile("migrate")
public class SqliteToPostgresMigrator implements CommandLineRunner {

    // Keep in sync with the boolean columns listed in
    // scripts (build tooling)/sqlite_to_postgres.js used to generate db/migration/postgres/*.sql -
    // these are the columns that are BOOLEAN in Postgres but INTEGER 0/1 in the source SQLite file.
    private static final Set<String> BOOLEAN_COLUMNS = Set.of(
            "is_correct", "active", "anonymous", "pinned", "has_projector", "has_ac",
            "demo_content", "published", "default_home", "notify_parent", "notify_teacher",
            "completed", "shuffle_questions", "shuffle_options", "fullscreen", "disable_copy",
            "detect_tab_switch", "needs_manual_grade", "show_correct_answers", "allow_late", "correct"
    );

    // Every table Flyway creates (db/migration/postgres/V1..V26). Update this list if a later
    // migration adds a table before you run a fresh cutover. Order doesn't matter - FK
    // enforcement is disabled for the duration of the load.
    private static final List<String> TABLES = List.of(
            "tenants", "branches", "rooms", "users", "students", "guardians", "student_guardians",
            "courses", "course_modules", "lessons", "lesson_materials", "lesson_progress",
            "study_groups", "enrollments", "class_sessions", "attendance_records",
            "questions", "question_options", "exams", "exam_questions", "student_exams", "student_answers",
            "assignments", "submissions", "grade_items",
            "invoices", "installments", "payments",
            "notifications", "notification_rules",
            "risk_assessments", "student_timeline", "audit_logs",
            "messages", "announcements", "calendar_events",
            "student_points", "point_events", "badges", "student_badges", "certificates", "teacher_evaluations",
            "course_purchase_orders", "forum_topics", "forum_replies", "forum_likes",
            "teacher_academies", "student_gate_logs", "contact_leads",
            "support_cases", "support_messages", "password_reset_tokens",
            "submission_files", "grading_comments", "video_watch_sessions",
            "lesson_checkpoints", "lesson_checkpoint_answers", "uploaded_files",
            "course_access_codes"
    );

    private final DataSource postgres;
    private final String sqlitePath;

    public SqliteToPostgresMigrator(DataSource postgres,
                                     org.springframework.core.env.Environment env) {
        this.postgres = postgres;
        this.sqlitePath = env.getRequiredProperty("manarah.migrate.sqlite-path");
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== SQLite -> PostgreSQL data migration ===");
        System.out.println("Source: " + sqlitePath);

        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
             Connection pg = postgres.getConnection()) {

            pg.setAutoCommit(false);
            try (Statement s = pg.createStatement()) {
                s.execute("SET session_replication_role = replica");
            }

            for (String table : TABLES) {
                copyTable(sqlite, pg, table);
            }

            pg.commit();

            try (Statement s = pg.createStatement()) {
                s.execute("SET session_replication_role = DEFAULT");
            }
            resyncIdentitySequences(pg);
            pg.commit();
        }

        System.out.println("=== Done ===");
    }

    private void copyTable(Connection sqlite, Connection pg, String table) throws SQLException {
        try (Statement check = pg.createStatement();
             ResultSet existing = check.executeQuery("SELECT count(*) FROM " + table)) {
            existing.next();
            if (existing.getLong(1) > 0) {
                System.out.println(table + ": target already has rows, skipping");
                return;
            }
        }

        try (Statement src = sqlite.createStatement();
             ResultSet rs = src.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            LinkedHashSet<String> columns = new LinkedHashSet<>();
            for (int i = 1; i <= columnCount; i++) columns.add(meta.getColumnName(i));

            String columnList = String.join(", ", columns);
            String placeholders = String.join(", ", columns.stream().map(c -> "?").toArray(String[]::new));
            String insertSql = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";

            long rowCount = 0;
            try (PreparedStatement insert = pg.prepareStatement(insertSql)) {
                while (rs.next()) {
                    int i = 1;
                    for (String col : columns) {
                        bind(insert, i++, col, rs);
                    }
                    insert.addBatch();
                    rowCount++;
                    if (rowCount % 500 == 0) insert.executeBatch();
                }
                insert.executeBatch();
            }
            System.out.println(table + ": copied " + rowCount + " rows");
        }
    }

    private void bind(PreparedStatement insert, int index, String column, ResultSet rs) throws SQLException {
        if (BOOLEAN_COLUMNS.contains(column)) {
            Object raw = rs.getObject(column);
            if (raw == null) {
                insert.setNull(index, Types.BOOLEAN);
            } else {
                insert.setBoolean(index, ((Number) raw).intValue() != 0);
            }
            return;
        }
        insert.setObject(index, rs.getObject(column));
    }

    private void resyncIdentitySequences(Connection pg) throws SQLException {
        for (String table : TABLES) {
            try (Statement s = pg.createStatement()) {
                s.execute(
                        "SELECT setval(pg_get_serial_sequence('" + table + "','id'), " +
                        "COALESCE((SELECT MAX(id) FROM " + table + "), 0) + 1, false)");
            }
        }
        System.out.println("Resynced identity sequences for " + TABLES.size() + " tables");
    }
}
