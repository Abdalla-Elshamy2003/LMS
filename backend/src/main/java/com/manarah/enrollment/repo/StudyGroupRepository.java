package com.manarah.enrollment.repo;

import com.manarah.enrollment.domain.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
    List<StudyGroup> findByTenantId(Long tenantId);
    List<StudyGroup> findByTenantIdAndCourseId(Long tenantId, Long courseId);
    Optional<StudyGroup> findByTenantIdAndId(Long tenantId, Long id);
}
