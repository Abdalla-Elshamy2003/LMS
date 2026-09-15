package com.manarah.exam;
import com.manarah.common.tenant.TenantContext;
import com.manarah.exam.repo.StudentExamRepository;
import com.manarah.exam.repo.ExamRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ExamExpiryJob {
    private final StudentExamRepository attempts;
    private final ExamRepository exams;
    private final ExamService service;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExamExpiryJob.class);
    public ExamExpiryJob(StudentExamRepository attempts, ExamRepository exams, ExamService service) {
        this.attempts=attempts; this.exams=exams; this.service=service;
    }
    @Scheduled(fixedDelayString = "${manarah.exams.expiry-interval-ms:30000}", initialDelay = 30000)
    public void finishExpired() {
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
