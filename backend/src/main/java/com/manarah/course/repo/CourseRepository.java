package com.manarah.course.repo;

import com.manarah.course.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTenantId(Long tenantId);

    /** Rolls up managed academy tenants alongside the caller's own — see AcademyAccess.visibleTenantIds. */
    List<Course> findByTenantIdIn(List<Long> tenantIds);
    List<Course> findByTenantIdAndTeacherId(Long tenantId, Long teacherId);

    /** The catalogue for one school year — what a student sees on their dashboard. */
    List<Course> findByTenantIdAndGradeAndStatus(Long tenantId, String grade, String status);
    Optional<Course> findByTenantIdAndId(Long tenantId, Long id);
    long countByTenantId(Long tenantId);
    long countByTenantIdIn(List<Long> tenantIds);
}
