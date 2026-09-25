package com.manarah.common.events;

import java.time.Instant;
import java.util.List;

/**
 * Domain events published inside transactional service methods and consumed AFTER_COMMIT by the
 * notification rules engine (§34), risk detection (§17), student timeline (§18), gamification (§30)
 * and audit (§48). Keeping these cross-cutting concerns as subscribers means core services never
 * import them.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    public interface StudentEvent {
        Long tenantId();
        Long studentId();
    }

    /** A course was just published: the students of its school year hear about it. */
    public record CourseOffered(Long tenantId, Long courseId) {
    }

    /** A lesson that is already open was just added to a course: the students studying it hear about it. */
    public record LessonAdded(Long tenantId, Long courseId, Long lessonId) {
    }

    /** A student account was just created through self-registration, checkout or code redemption. */
    public record StudentRegistered(Long tenantId, Long studentId) {
    }

    public record StudentAbsent(Long tenantId, Long studentId, Long sessionId, String courseTitle, Instant when)
            implements StudentEvent {
    }

    public record StudentLate(Long tenantId, Long studentId, Long sessionId, String courseTitle, int lateMinutes, Instant when)
            implements StudentEvent {
    }

    public record ScoreRecorded(Long tenantId, Long studentId, Long courseId, String title, String category,
                                double score, double maxScore, String sourceType, Long sourceId)
            implements StudentEvent {
        public double percent() {
            return maxScore > 0 ? Math.round(score / maxScore * 1000) / 10.0 : 0;
        }
    }

    public record HomeworkMissed(Long tenantId, Long studentId, Long assignmentId, String title, int missedCount)
            implements StudentEvent {
    }

    public record HomeworkSubmitted(Long tenantId, Long studentId, Long assignmentId, String title, boolean late)
            implements StudentEvent {
    }

    public record ExamSubmitted(Long tenantId, Long studentId, Long examId, String title, double score, double maxScore)
            implements StudentEvent {
    }

    public record EnrollmentCreated(Long tenantId, Long studentId, Long courseId, String courseTitle)
            implements StudentEvent {
    }

    public record PaymentRecorded(Long tenantId, Long studentId, Long invoiceId, java.math.BigDecimal amount)
            implements StudentEvent {
    }

    public record InstallmentDue(Long tenantId, Long studentId, Long installmentId, java.math.BigDecimal amount, String dueDate)
            implements StudentEvent {
    }

    /** Fired by risk detection when a student crosses a risk threshold — a state transition, not a level. */
    public record RiskChanged(Long tenantId, Long studentId, String oldLevel, String newLevel, List<String> reasons)
            implements StudentEvent {
    }

    /** Generic signal that a student's inputs changed and derived metrics should be recomputed. */
    public record StudentMetricsDirty(Long tenantId, Long studentId) implements StudentEvent {
    }
}
