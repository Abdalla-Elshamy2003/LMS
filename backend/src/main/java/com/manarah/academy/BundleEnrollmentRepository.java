package com.manarah.academy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BundleEnrollmentRepository extends JpaRepository<BundleEnrollment, Long> {
    List<BundleEnrollment> findBySubscriptionId(Long subscriptionId);
    boolean existsBySubscriptionIdAndEnrollmentId(Long subscriptionId, Long enrollmentId);
}
