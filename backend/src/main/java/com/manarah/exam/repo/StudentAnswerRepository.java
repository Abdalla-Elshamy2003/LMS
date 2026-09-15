package com.manarah.exam.repo;

import com.manarah.exam.domain.StudentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {
    List<StudentAnswer> findByTenantIdAndStudentExamId(Long tenantId, Long studentExamId);
    Optional<StudentAnswer> findByTenantIdAndStudentExamIdAndQuestionId(Long tenantId, Long studentExamId, Long questionId);
    List<StudentAnswer> findByTenantIdAndStudentExamIdIn(Long tenantId, List<Long> studentExamIds);
    void deleteByTenantIdAndStudentExamId(Long tenantId, Long studentExamId);
}
