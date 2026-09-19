package com.manarah.exam;
import com.manarah.common.tenant.TenantContext;
import com.manarah.exam.repo.StudentExamRepository;
import com.manarah.exam.repo.ExamRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ExamExpiryJob {
    // Arbitrary fixed key identifying this job for Postgres advisory locking - only needs to be
    // unique among this app's own advisory locks, not globally.
    private static final long LOCK_KEY = 851203L;

    private final StudentExamRepository attempts;
    private final ExamRepository exams;
    private final ExamService service;
    private final JdbcTemplate jdbc;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExamExpiryJob.class);
    public ExamExpiryJob(StudentExamRepository attempts, ExamRepository exams, ExamService service, JdbcTemplate jdbc) {
        this.attempts=attempts; this.exams=exams; this.service=service; this.jdbc=jdbc;
    }

    @Scheduled(fixedDelayString = "${manarah.exams.expiry-interval-ms:30000}", initialDelay = 30000)
    public void finishExpired() {
        // With more than one backend instance, every instance fires this on the same schedule -
        // an advisory lock keeps only one of them actually doing the work per cycle. If the lock call itself
        // fails, run unconditionally rather than skip the work.
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, LOCK_KEY));
        } catch (DataAccessException e) {
            locked = true;
        }
        if (!locked) return;
        try {
            doFinishExpired();
        } finally {
            try { jdbc.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, LOCK_KEY); }
            catch (DataAccessException ignored) {}
        }
    }

    private void doFinishExpired() {
        for (var attempt : attempts.findByStatus("IN_PROGRESS")) {
            var exam = exams.findByTenantIdAndId(attempt.getTenantId(),attempt.getExamId()).orElse(null);
            if (exam == null || attempt.getStartedAt() == null) continue;
            Instant deadline = attempt.getStartedAt().plus(exam.getDurationMinutes(), ChronoUnit.MINUTES);
            if (exam.getEndAt() != null && exam.getEndAt().isBefore(deadline)) deadline=exam.getEndAt();
            if (Instant.now().isBefore(deadline)) continue;
            try { TenantContext.set(attempt.getTenantId()); service.submit(attempt.getId(),attempt.getStudentId(),new ExamDtos.SubmitRequest(List.of(),attempt.getTabSwitches())); }
            catch (Exception e) { log.warn("Exam expiry retry required for attempt {} ({})",attempt.getId(),e.getClass().getSimpleName()); }
            finally { TenantContext.clear(); }
        }
    }
}
