package com.manarah.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanEnrollmentRepository extends JpaRepository<PlanEnrollment, Long> {
    List<PlanEnrollment> findByPlanIdAndStudentId(Long planId, Long studentId);
    List<PlanEnrollment> findByEnrollmentId(Long enrollmentId);
    boolean existsByPlanIdAndEnrollmentId(Long planId, Long enrollmentId);
}
