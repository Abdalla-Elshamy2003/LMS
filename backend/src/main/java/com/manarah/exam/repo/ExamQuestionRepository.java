package com.manarah.exam.repo;

import com.manarah.exam.domain.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    List<ExamQuestion> findByTenantIdAndExamIdOrderByPosition(Long tenantId, Long examId);
    boolean existsByTenantIdAndExamIdAndQuestionId(Long tenantId, Long examId, Long questionId);
    long countByTenantIdAndExamId(Long tenantId, Long examId);
    List<ExamQuestion> findByTenantIdAndQuestionId(Long tenantId, Long questionId);
    java.util.Optional<ExamQuestion> findByTenantIdAndExamIdAndQuestionId(Long tenantId, Long examId, Long questionId);
}
