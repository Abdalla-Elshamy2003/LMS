package com.manarah.integration;

import com.manarah.common.events.DomainEvents;
import com.manarah.gradebook.GradebookService;
import com.manarah.risk.RiskService;
import com.manarah.student.StudentMetricsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles a scored exam/homework end to end in ONE listener so ordering is guaranteed by code, not
 * by Spring's (unspecified) cross-event synchronization order: first write the grade to the
 * gradebook ledger (§15), then recompute the student's materialised metrics and reassess risk so
 * the fresh grade is included. Score-producing services therefore publish only {@code ScoreRecorded}
 * (not {@code StudentMetricsDirty}); non-score signals use {@link MetricsRiskListener}.
 */
@Component
public class ScoreListener {

    private final GradebookService gradebook;
    private final StudentMetricsService metrics;
    private final RiskService risk;

    public ScoreListener(GradebookService gradebook, StudentMetricsService metrics, RiskService risk) {
        this.gradebook = gradebook;
        this.metrics = metrics;
        this.risk = risk;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScore(DomainEvents.ScoreRecorded e) {
        gradebook.upsertFromScore(e);
        metrics.recompute(e.tenantId(), e.studentId());
        risk.assess(e.tenantId(), e.studentId());
    }
}
