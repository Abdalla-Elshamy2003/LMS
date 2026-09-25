package com.manarah.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentSubmissionRepository extends JpaRepository<PaymentSubmission, Long> {
    List<PaymentSubmission> findByTenantIdInOrderByIdDesc(Collection<Long> tenantIds);
    Optional<PaymentSubmission> findFirstByPlanSubscriptionIdOrderByIdDesc(Long planSubscriptionId);
}
