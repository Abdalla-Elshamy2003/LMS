package com.manarah.org.repo;

import com.manarah.org.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByTenantIdOrderByName(Long tenantId);
    Optional<Branch> findByTenantIdAndId(Long tenantId, Long id);
    long countByTenantId(Long tenantId);
}
