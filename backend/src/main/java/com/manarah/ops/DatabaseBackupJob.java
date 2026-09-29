package com.manarah.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The nightly database backup, run by the backend itself (04:00 Egypt time):
 * <ol>
 *   <li>pg_dump the whole database (custom format, compressed);</li>
 *   <li>upload it to the storage bucket under {@code backups/postgres/};</li>
 *   <li>prove it restores — load it into a scratch database on the same server and compare row counts of the tables
 *       that hold people, money, grades and attendance with the live ones, then drop the scratch database;</li>
 *   <li>delete backups older than {@code manarah.backup.keep-days} (30).</li>
 * </ol>
 * A failure is logged as an ERROR (so it reaches Sentry), is shown on head office's «حالة النظام», and — because no
 * fresh backup lands — turns {@code /api/public/health/backup} red for the uptime monitor. Needs the PostgreSQL client
 * tools (installed in the backend image) and the storage bucket; without either it simply doesn't run.
 * The database password reaches the tools through their environment, never their command line.
 */
@Component
public class DatabaseBackupJob {
    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupJob.class);
    static final String SCRATCH_DB = "droos_restore_check";
    private static final List<String> CHECKED = List.of("users", "students", "enrollments", "courses", "payment_submissions",
            "payments", "attendance_records");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    private final BackupStatus bucket;
    private final boolean enabled;
    private final String jdbcUrl, username, password;
    private final int keepDays;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Boolean toolsPresent;
    private volatile LastRun lastRun;

    public record LastRun(Instant at, boolean ok, String message) {}

    public DatabaseBackupJob(BackupStatus bucket, @Value("${manarah.backup.enabled:true}") boolean enabled,
                             @Value("${spring.datasource.url:}") String jdbcUrl, @Value("${spring.datasource.username:}") String username,
                             @Value("${spring.datasource.password:}") String password, @Value("${manarah.backup.keep-days:30}") int keepDays) {
        this.bucket = bucket; this.enabled = enabled; this.jdbcUrl = jdbcUrl; this.username = username; this.password = password;
        this.keepDays = keepDays;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void nightly() {
        if (available()) runSafely();
    }

    /** Head office's "back up now": runs in the background. False when one is already running or this server can't. */
    public boolean startNow() {
        if (!available() || running.get()) return false;
        CompletableFuture.runAsync(this::runSafely);
        return true;
    }

    public boolean available() {
        return enabled && bucket.configured() && jdbcUrl.startsWith("jdbc:postgresql:") && toolsPresent();
    }

    public boolean isRunning() { return running.get(); }

    public LastRun lastRun() { return lastRun; }

    private void runSafely() {
        if (!running.compareAndSet(false, true)) return;
        try {
            String done = run();
            lastRun = new LastRun(Instant.now(), true, done);
            log.info("[backup] {}", done);
        } catch (Exception e) {
            lastRun = new LastRun(Instant.now(), false, String.valueOf(e.getMessage()));
            log.error("[backup] nightly database backup failed", e);
        } finally {
            running.set(false);
            bucket.invalidate();
        }
    }

    private String run() throws Exception {
        URI db = URI.create(jdbcUrl.substring("jdbc:".length()));
        String database = db.getPath().replaceFirst("^/", "");
        Map<String, String> env = new HashMap<>();
        env.put("PGHOST", db.getHost());
        env.put("PGPORT", String.valueOf(db.getPort() > 0 ? db.getPort() : 5432));
        env.put("PGUSER", username);
        env.put("PGPASSWORD", password);
        String sslmode = param(db.getRawQuery(), "sslmode");
        if (sslmode != null) env.put("PGSSLMODE", sslmode);

        String name = "droos-" + STAMP.format(Instant.now()) + ".dump";
        Path file = Files.createTempFile("droos-backup-", ".dump");
        try (S3Client s3 = bucket.client()) {
            exec(env, "pg_dump", "--format=custom", "--compress=9", "--no-owner", "--no-privileges", "--dbname=" + database, "--file=" + file);
            if (!exec(env, "pg_restore", "--list", file.toString()).contains("TABLE DATA"))
                throw new IllegalStateException("the dump has no table data — not keeping it");
            long size = Files.size(file);
            s3.putObject(PutObjectRequest.builder().bucket(bucket.bucket()).key(BackupStatus.PREFIX + name).build(), RequestBody.fromFile(file));
            String check = verify(env, database, file);
            int removed = prune(s3);
            return name + " (" + size + " bytes) uploaded; " + check + (removed > 0 ? "; removed " + removed + " old" : "");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Restores the dump into a scratch database next to the real one and compares row counts. */
    private String verify(Map<String, String> env, String database, Path file) throws Exception {
        // The scratch database's name is fixed, so this can never overwrite the real one.
        if (SCRATCH_DB.equals(database)) throw new IllegalStateException("refusing to restore over " + database);
        exec(env, "psql", "--dbname=postgres", "-v", "ON_ERROR_STOP=1", "-q",
                "-c", "DROP DATABASE IF EXISTS " + SCRATCH_DB, "-c", "CREATE DATABASE " + SCRATCH_DB);
        try {
            exec(env, "pg_restore", "--no-owner", "--no-privileges", "--exit-on-error", "--dbname=" + SCRATCH_DB, file.toString());
            List<String> counts = new ArrayList<>();
            for (String table : CHECKED) {
                Long live = count(env, database, table), restored = count(env, SCRATCH_DB, table);
                if (live == null && restored == null) continue;
                // Rows written between the dump and this check can make the live count a little higher, never lower.
                if (live == null || restored == null || restored > live || live - restored > 50)
                    throw new IllegalStateException("restore check failed on " + table + ": live=" + live + " restored=" + restored);
                counts.add(table + "=" + restored);
            }
            return "restore check passed (" + String.join(", ", counts) + ")";
        } finally {
            try { exec(env, "psql", "--dbname=postgres", "-q", "-c", "DROP DATABASE IF EXISTS " + SCRATCH_DB); }
            catch (Exception e) { log.warn("[backup] could not drop {}: {}", SCRATCH_DB, e.getMessage()); }
        }
    }

    private Long count(Map<String, String> env, String database, String table) {
        try { return Long.parseLong(exec(env, "psql", "--dbname=" + database, "-tA", "-c", "SELECT count(*) FROM " + table).trim()); }
        catch (Exception e) { return null; }
    }

    private int prune(S3Client s3) {
        String cutoff = STAMP.format(Instant.now().minus(keepDays, ChronoUnit.DAYS));
        int removed = 0;
        String token = null;
        do {
            var page = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket.bucket()).prefix(BackupStatus.PREFIX).continuationToken(token).build());
            for (S3Object o : page.contents()) {
                String stamp = o.key().substring(BackupStatus.PREFIX.length()).replaceFirst("^droos-", "").replaceFirst("\\.dump$", "");
                if (stamp.compareTo(cutoff) < 0) {
                    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket.bucket()).key(o.key()).build());
                    removed++;
                }
            }
            token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (token != null);
        return removed;
    }

    private boolean toolsPresent() {
        if (toolsPresent == null) {
            try {
                Process p = new ProcessBuilder("pg_dump", "--version").redirectErrorStream(true).start();
                toolsPresent = p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) {
                toolsPresent = false;
            }
        }
        return toolsPresent;
    }

    /** Runs a PostgreSQL tool; returns its output, or throws with the end of it. */
    private static String exec(Map<String, String> env, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().putAll(env);
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(60, TimeUnit.MINUTES)) { p.destroyForcibly(); throw new IllegalStateException(command[0] + " timed out"); }
        String text = new String(out, StandardCharsets.UTF_8);
        if (p.exitValue() != 0)
            throw new IllegalStateException(command[0] + " failed: " + text.substring(Math.max(0, text.length() - 400)).trim());
        return text;
    }

    private static String param(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) return pair.substring(eq + 1);
        }
        return null;
    }
}
