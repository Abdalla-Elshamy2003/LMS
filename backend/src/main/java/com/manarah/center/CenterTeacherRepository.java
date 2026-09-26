package com.manarah.center;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CenterTeacherRepository extends JpaRepository<CenterTeacher, Long> {
    List<CenterTeacher> findByTenantIdOrderByName(Long tenantId);
    Optional<CenterTeacher> findByTenantIdAndId(Long tenantId, Long id);
    long countByTenantId(Long tenantId);
}
