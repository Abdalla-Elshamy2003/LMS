package com.manarah.communication.repo;

import com.manarah.communication.domain.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {
    List<SupportCase> findByTenantIdOrderByLastMessageAtDesc(Long tenantId);
    Optional<SupportCase> findByTenantIdAndId(Long tenantId, Long id);
}
