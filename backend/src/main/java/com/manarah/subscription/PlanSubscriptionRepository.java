package com.manarah.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PlanSubscriptionRepository extends JpaRepository<PlanSubscription, Long> {
    List<PlanSubscription> findByPlanIdAndStudentIdOrderByIdDesc(Long planId, Long studentId);
    List<PlanSubscription> findByPlanIdOrderByIdDesc(Long planId);
    List<PlanSubscription> findByTenantIdAndStatus(Long tenantId, String status);
    List<PlanSubscription> findByStatus(String status);
    List<PlanSubscription> findByStudentIdInOrderByIdDesc(Collection<Long> studentIds);
    List<PlanSubscription> findByPlanIdAndStatus(Long planId, String status);
}
