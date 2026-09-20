package com.manarah.exam.repo;

import com.manarah.exam.domain.StudentExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentExamRepository extends JpaRepository<StudentExam, Long> {
    List<StudentExam> findByStatus(String status);
    Optional<StudentExam> findByTenantIdAndExamIdAndStudentId(Long tenantId, Long examId, Long studentId);
    Optional<StudentExam> findByTenantIdAndId(Long tenantId, Long id);
    List<StudentExam> findByTenantIdAndExamId(Long tenantId, Long examId);
    List<StudentExam> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    long countByTenantIdAndExamId(Long tenantId, Long examId);
    long countByTenantIdAndStatus(Long tenantId, String status);
}
