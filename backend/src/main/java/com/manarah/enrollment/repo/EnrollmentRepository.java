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
    long countByTenantIdAndCourseIdAndStatusIn(Long tenantId, Long courseId, java.util.Collection<String> statuses);

    /** Students actually studying a course — not the ones still waiting to pay for it. */
    java.util.Set<String> STUDYING = java.util.Set.of("ACTIVE", "COMPLETED");

    default long countStudying(Long tenantId, Long courseId) {
        return countByTenantIdAndCourseIdAndStatusIn(tenantId, courseId, STUDYING);
    }
    long countByTenantId(Long tenantId);
}
