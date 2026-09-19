package com.manarah.integration;

import com.manarah.common.events.DomainEvents;
import com.manarah.risk.RiskService;
import com.manarah.student.StudentMetricsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recomputes materialised metrics and reassesses risk after the triggering transaction commits.
 * Runs in a fresh transaction (the original is already committed), so it never extends the original write.
 */
@Component
public class MetricsRiskListener {

    private final StudentMetricsService metrics;
    private final RiskService risk;

    public MetricsRiskListener(StudentMetricsService metrics, RiskService risk) {
        this.metrics = metrics;
        this.risk = risk;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDirty(DomainEvents.StudentMetricsDirty e) {
        metrics.recompute(e.tenantId(), e.studentId());
        risk.assess(e.tenantId(), e.studentId());
    }
}
