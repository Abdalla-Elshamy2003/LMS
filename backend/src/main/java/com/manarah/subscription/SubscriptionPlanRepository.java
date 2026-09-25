package com.manarah.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findByTenantId(Long tenantId);
    Optional<SubscriptionPlan> findByTenantIdAndYearKeyAndSubjectKey(Long tenantId, String yearKey, String subjectKey);
}
