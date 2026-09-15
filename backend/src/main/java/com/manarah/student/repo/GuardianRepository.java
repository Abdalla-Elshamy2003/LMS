package com.manarah.student.repo;

import com.manarah.student.domain.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
    List<Guardian> findByTenantId(Long tenantId);
    Optional<Guardian> findByTenantIdAndId(Long tenantId, Long id);
    Optional<Guardian> findByTenantIdAndUserId(Long tenantId, Long userId);
}
