package com.manarah.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Unauthenticated endpoints for operations: an uptime probe, and what the browser app needs to report errors. */
@RestController
@RequestMapping("/api/public")
public class OpsPublicController {
    private final JdbcTemplate jdbc;
    private final BackupStatus backups;
    private final String frontendDsn;
    private final String environment;

    public OpsPublicController(JdbcTemplate jdbc, BackupStatus backups, @Value("${SENTRY_FRONTEND_DSN:}") String frontendDsn,
                               @Value("${sentry.environment:production}") String environment) {
        this.jdbc = jdbc; this.backups = backups;
        this.frontendDsn = frontendDsn == null ? "" : frontendDsn.trim(); this.environment = environment;
    }

    /**
     * For the uptime monitor: 200 when the app answers and its database does, 503 otherwise. Goes through the same
     * path a student's request does (Cloudflare, the web server, the app, the database), so "up" means usable.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status", "UP"));
        } catch (Exception e) {
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(Map.of("status", "DOWN"));
        }
    }

    /**
     * For a second uptime monitor: 200 while the newest nightly database backup is under 36 hours old, 503 once a
     * night was missed — so a stopped backup job emails the owner instead of going unnoticed. Says nothing else.
     */
    @GetMapping("/health/backup")
    public ResponseEntity<Map<String, String>> backupHealth() {
        boolean fresh = backups.summary().fresh();
        return ResponseEntity.status(fresh ? 200 : 503).cacheControl(CacheControl.noStore())
                .body(Map.of("status", fresh ? "UP" : "STALE"));
    }

    /** The browser app's error reporting: Sentry's public DSN for the frontend project (blank = off). */
    @GetMapping("/client-config")
    public Map<String, Object> clientConfig() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sentryDsn", frontendDsn);
        out.put("environment", environment);
        return out;
    }
}
