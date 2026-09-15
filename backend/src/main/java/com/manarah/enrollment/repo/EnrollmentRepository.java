package com.manarah.enrollment.repo;

import com.manarah.enrollment.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    List<Enrollment> findByTenantIdAndCourseId(Long tenantId, Long courseId);
    Optional<Enrollment> findByTenantIdAndStudentIdAndCourseId(Long tenantId, Long studentId, Long courseId);
    boolean existsByTenantIdAndStudentIdAndCourseId(Long tenantId, Long studentId, Long courseId);
    long countByTenantIdAndCourseId(Long tenantId, Long courseId);
    long countByTenantId(Long tenantId);
}
