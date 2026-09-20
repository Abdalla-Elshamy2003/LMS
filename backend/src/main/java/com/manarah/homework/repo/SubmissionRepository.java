package com.manarah.homework.repo;

import com.manarah.homework.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByTenantIdAndAssignmentId(Long tenantId, Long assignmentId);
    List<Submission> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    Optional<Submission> findByTenantIdAndAssignmentIdAndStudentId(Long tenantId, Long assignmentId, Long studentId);
    Optional<Submission> findByTenantIdAndFileKey(Long tenantId, String fileKey);
    long countByTenantIdAndStudentId(Long tenantId, Long studentId);
    long countByTenantIdAndStudentIdAndStatus(Long tenantId, Long studentId, String status);
    long countByTenantIdAndStatusIn(Long tenantId, java.util.Collection<String> statuses);
}
