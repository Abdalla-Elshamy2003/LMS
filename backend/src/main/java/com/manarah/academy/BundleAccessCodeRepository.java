package com.manarah.academy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BundleAccessCodeRepository extends JpaRepository<BundleAccessCode, Long> {
    Optional<BundleAccessCode> findByCodeIgnoreCase(String code);
    boolean existsByCode(String code);
    List<BundleAccessCode> findByBundleIdOrderByCreatedAtDesc(Long bundleId);
    long countByBundleIdAndStatus(Long bundleId, String status);
}
