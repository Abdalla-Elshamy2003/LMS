package com.manarah.tooling;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * One-time data copy from the legacy SQLite database into the PostgreSQL database this app now
 * runs on. Only active on the 'migrate' profile (see application-migrate.yml).
 *
 * <p>Run against the 'postgres' profile's own datasource (so it targets whatever
 * MANARAH_DB_URL/USERNAME/PASSWORD point at) plus manarah.migrate.sqlite-path pointing at the
 * SQLite file to read from, e.g.:
 * <pre>
 * java -jar manarah-lms.jar --spring.profiles.active=postgres,migrate \
 *      --manarah.migrate.sqlite-path=/path/to/manarah.db
 * </pre>
 *
 * <p>The tables to copy and which columns are booleans are read from the Postgres schema that
 * Flyway just created, never from a hand-kept list, so a new migration cannot silently be left
 * out. SQLite is opened read-only - it stays untouched as the rollback copy. Tables absent from
 * the source are skipped, and a table that already has rows in Postgres is skipped too, so a
 * failed run can simply be repeated. Foreign key and trigger enforcement is off for the load
 * (session_replication_role=replica) so tables can be copied in any order; identity sequences are
 * then resynced to MAX(id)+1 so rows created after the cutover cannot collide with migrated ids.
 *
 * <p>Deliberately un-clever JDBC (no Hibernate): every column is copied generically, and only
 * SQLite's 0/1 integers are converted where the Postgres column is a real BOOLEAN. Timestamps are
 * text in both databases and are copied verbatim.
 */
@Component
@Profile("migrate")
public class SqliteToPostgresMigrator implements CommandLineRunner {

    private final DataSource postgres;
    private final String sqlitePath;
    private final ConfigurableApplicationContext context;

    public SqliteToPostgresMigrator(DataSource postgres, org.springframework.core.env.Environment env,
                                     ConfigurableApplicationContext context) {
        this.postgres = postgres;
        this.sqlitePath = env.getRequiredProperty("manarah.migrate.sqlite-path");
        this.context = context;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== SQLite -> PostgreSQL data migration ===");
        System.out.println("Source: " + sqlitePath);

        // Read-only: the SQLite file is the rollback copy and must never be modified by the migration.
        Properties readOnly = new Properties();
        readOnly.setProperty("open_mode", "1");
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath, readOnly);
             Connection pg = postgres.getConnection()) {

            List<String> tables = targetTables(pg);
            pg.setAutoCommit(false);
            try (Statement s = pg.createStatement()) {
                s.execute("SET session_replication_role = replica");
            }

            for (String table : tables) {
                copyTable(sqlite, pg, table);
            }
            pg.commit();

            try (Statement s = pg.createStatement()) {
                s.execute("SET session_replication_role = DEFAULT");
            }
            resyncIdentitySequences(pg, tables);
            pg.commit();
        }

        System.out.println("=== Done ===");
        // The app context (web stack, schedulers) would otherwise keep the process alive: close it and exit 0.
        System.exit(SpringApplication.exit(context));
    }

    /** Every table Flyway created in the target schema, except Flyway's own bookkeeping. */
    private static List<String> targetTables(Connection pg) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement s = pg.createStatement();
             ResultSet rs = s.executeQuery("SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' "
                     + "AND table_name <> 'flyway_schema_history' ORDER BY table_name")) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        return tables;
    }

    private static Set<String> booleanColumns(Connection pg, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (PreparedStatement ps = pg.prepareStatement("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = ? AND data_type = 'boolean'")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) columns.add(rs.getString(1));
            }
        }
        return columns;
    }

    private void copyTable(Connection sqlite, Connection pg, String table) throws SQLException {
        if (!existsIn(sqlite, table)) {
            System.out.println(table + ": not in source database, skipping");
            return;
        }
        try (Statement check = pg.createStatement();
             ResultSet existing = check.executeQuery("SELECT count(*) FROM " + table)) {
            existing.next();
            if (existing.getLong(1) > 0) {
                System.out.println(table + ": target already has rows, skipping");
                return;
            }
        }

        Set<String> booleans = booleanColumns(pg, table);
        try (Statement src = sqlite.createStatement();
             ResultSet rs = src.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {

            ResultSetMetaData meta = rs.getMetaData();
            LinkedHashSet<String> columns = new LinkedHashSet<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnName(i));

            String columnList = String.join(", ", columns);
            String placeholders = String.join(", ", columns.stream().map(c -> "?").toArray(String[]::new));
            // Ids are GENERATED ALWAYS AS IDENTITY, so keeping the original ids needs an explicit override.
            String insertSql = "INSERT INTO " + table + " (" + columnList + ") OVERRIDING SYSTEM VALUE VALUES (" + placeholders + ")";

            long rowCount = 0;
            try (PreparedStatement insert = pg.prepareStatement(insertSql)) {
                while (rs.next()) {
                    int i = 1;
                    for (String col : columns) bind(insert, i++, col, rs, booleans.contains(col));
                    insert.addBatch();
                    rowCount++;
                    if (rowCount % 500 == 0) insert.executeBatch();
                }
                insert.executeBatch();
            }
            System.out.println(table + ": copied " + rowCount + " rows");
        }
    }

    private static boolean existsIn(Connection sqlite, String table) throws SQLException {
        try (PreparedStatement ps = sqlite.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void bind(PreparedStatement insert, int index, String column, ResultSet rs, boolean isBoolean) throws SQLException {
        Object raw = rs.getObject(column);
        if (isBoolean) {
            if (raw == null) insert.setNull(index, Types.BOOLEAN);
            else insert.setBoolean(index, ((Number) raw).intValue() != 0);
        } else {
            insert.setObject(index, raw);
        }
    }

    private static void resyncIdentitySequences(Connection pg, List<String> tables) throws SQLException {
        for (String table : tables) {
            try (Statement s = pg.createStatement()) {
                s.execute("SELECT setval(pg_get_serial_sequence('" + table + "','id'), "
                        + "COALESCE((SELECT MAX(id) FROM " + table + "), 0) + 1, false)");
            }
        }
        System.out.println("Resynced identity sequences for " + tables.size() + " tables");
    }
}
