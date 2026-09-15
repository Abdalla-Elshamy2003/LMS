package com.manarah.gamification.repo;

import com.manarah.gamification.domain.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BadgeRepository extends JpaRepository<Badge, Long> {
    List<Badge> findByTenantId(Long tenantId);
    Optional<Badge> findByTenantIdAndCode(Long tenantId, String code);
}
