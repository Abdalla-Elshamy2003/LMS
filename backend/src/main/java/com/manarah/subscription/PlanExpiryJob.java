package com.manarah.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Ends subscription periods that ran out (locking the courses they opened) and reminds students a few days before.
 * Every backend instance fires on the same schedule; a Postgres advisory lock lets one of them do the work.
 */
@Component
public class PlanExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(PlanExpiryJob.class);
    private static final long LOCK_KEY = 851204L;

    private final PlanAccess access;
    private final JdbcTemplate jdbc;

    public PlanExpiryJob(PlanAccess access, JdbcTemplate jdbc) { this.access = access; this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${manarah.subscriptions.expiry-interval-ms:600000}", initialDelay = 60000)
    public void run() {
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, LOCK_KEY));
        } catch (DataAccessException e) {
            locked = true;
        }
        if (!locked) return;
        try {
            Instant now = Instant.now();
            int ended = access.expire(now), reminded = access.remind(now);
            if (ended + reminded > 0) log.info("plan_expiry ended={} reminded={}", ended, reminded);
        } catch (RuntimeException e) {
            log.warn("plan_expiry outcome=error cause={}", e.toString());
        } finally {
            try { jdbc.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, LOCK_KEY); }
            catch (DataAccessException ignored) {}
        }
    }
}
