package com.manarah.center;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CenterStudentRepository extends JpaRepository<CenterStudent, Long> {
    List<CenterStudent> findByTenantIdOrderByIdDesc(Long tenantId);
    List<CenterStudent> findByTenantIdAndGroupId(Long tenantId, Long groupId);
    List<CenterStudent> findByTenantIdAndGroupIdIn(Long tenantId, List<Long> groupIds);
    Optional<CenterStudent> findByTenantIdAndId(Long tenantId, Long id);
    Optional<CenterStudent> findByTenantIdAndToken(Long tenantId, String token);
    Optional<CenterStudent> findByTenantIdAndCode(Long tenantId, String code);
    Optional<CenterStudent> findByToken(String token);
    boolean existsByToken(String token);
    boolean existsByTenantIdAndGroupId(Long tenantId, Long groupId);
    long countByTenantIdAndActiveTrue(Long tenantId);
}
