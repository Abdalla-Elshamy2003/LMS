package com.manarah.integration;

import com.manarah.common.events.DomainEvents;
import com.manarah.timeline.TimelineService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Appends student timeline entries (§18) as domain events occur. */
@Component
public class TimelineListener {

    private final TimelineService timeline;

    public TimelineListener(TimelineService timeline) {
        this.timeline = timeline;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAbsent(DomainEvents.StudentAbsent e) {
        timeline.record(e.tenantId(), e.studentId(), "ATTENDANCE", "غياب",
                "لم يحضر محاضرة " + e.courseTitle(), "user-x", e.when());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLate(DomainEvents.StudentLate e) {
        timeline.record(e.tenantId(), e.studentId(), "ATTENDANCE", "حضور متأخر",
                "تأخر " + e.lateMinutes() + " دقيقة في " + e.courseTitle(), "clock", e.when());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScore(DomainEvents.ScoreRecorded e) {
        timeline.record(e.tenantId(), e.studentId(), "EXAM", e.title(),
                "الدرجة: " + trim(e.score()) + "/" + trim(e.maxScore()) + " (" + e.percent() + "%)", "clipboard-check", null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHomeworkSubmitted(DomainEvents.HomeworkSubmitted e) {
        timeline.record(e.tenantId(), e.studentId(), "HOMEWORK", "تسليم واجب",
                e.title() + (e.late() ? " (متأخر)" : ""), "file-check", null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHomeworkMissed(DomainEvents.HomeworkMissed e) {
        timeline.record(e.tenantId(), e.studentId(), "HOMEWORK", "واجب غير مُسلَّم", e.title(), "file-x", null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEnrolled(DomainEvents.EnrollmentCreated e) {
        timeline.record(e.tenantId(), e.studentId(), "ENROLLMENT", "التحاق بكورس", e.courseTitle(), "book-open", null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPayment(DomainEvents.PaymentRecorded e) {
        timeline.record(e.tenantId(), e.studentId(), "PAYMENT", "دفعة مالية",
                "تم سداد " + e.amount() + " جنيه", "wallet", null);
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
