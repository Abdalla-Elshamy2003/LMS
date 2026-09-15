package com.manarah.exam.repo;

import com.manarah.exam.domain.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByTenantId(Long tenantId);
    List<Exam> findByTenantIdAndCourseId(Long tenantId, Long courseId);
    Optional<Exam> findByTenantIdAndId(Long tenantId, Long id);
    Optional<Exam> findByTenantIdAndPdfKey(Long tenantId, String pdfKey);
    long countByTenantId(Long tenantId);
}
