package com.manarah.integration;

import com.manarah.common.events.DomainEvents;
import com.manarah.notification.NotificationRulesEngine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Bridges domain events to the configurable notification rules engine (§34). */
@Component
public class NotificationRulesListener {

    private final NotificationRulesEngine engine;

    public NotificationRulesListener(NotificationRulesEngine engine) {
        this.engine = engine;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAbsent(DomainEvents.StudentAbsent e) {
        engine.onStudentAbsent(e.tenantId(), e.studentId(), e.courseTitle());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScore(DomainEvents.ScoreRecorded e) {
        engine.onLowScore(e.tenantId(), e.studentId(), e.title(), e.percent());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHomeworkMissed(DomainEvents.HomeworkMissed e) {
        engine.onHomeworkMissed(e.tenantId(), e.studentId(), e.title(), e.missedCount());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInstallmentDue(DomainEvents.InstallmentDue e) {
        engine.onInstallmentDue(e.tenantId(), e.studentId(), e.amount().toPlainString(), e.dueDate());
    }
}
