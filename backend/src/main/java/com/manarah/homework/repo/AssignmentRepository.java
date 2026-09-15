package com.manarah.homework.repo;

import com.manarah.homework.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByTenantId(Long tenantId);
    List<Assignment> findByTenantIdAndCourseId(Long tenantId, Long courseId);
    Optional<Assignment> findByTenantIdAndId(Long tenantId, Long id);
    Optional<Assignment> findByTenantIdAndFileKey(Long tenantId, String fileKey);
}
