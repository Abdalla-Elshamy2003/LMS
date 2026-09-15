package com.manarah.exam.repo;

import com.manarah.exam.domain.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    List<QuestionOption> findByTenantIdAndQuestionIdOrderByPosition(Long tenantId, Long questionId);
    List<QuestionOption> findByTenantIdAndQuestionIdIn(Long tenantId, List<Long> questionIds);
    void deleteByQuestionId(Long questionId);
}
