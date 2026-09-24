package com.manarah.academy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BundleSubscriptionRepository extends JpaRepository<BundleSubscription, Long> {
    Optional<BundleSubscription> findByBundleIdAndUserId(Long bundleId, Long userId);
    List<BundleSubscription> findByBundleIdOrderByCreatedAtDesc(Long bundleId);
    List<BundleSubscription> findByUserIdAndStatus(Long userId, String status);
    List<BundleSubscription> findByUserIdIn(Collection<Long> userIds);
    long countByBundleIdAndStatus(Long bundleId, String status);
    long countByStatus(String status);
}
