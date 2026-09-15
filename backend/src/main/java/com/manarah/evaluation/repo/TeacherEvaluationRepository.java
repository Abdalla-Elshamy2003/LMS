package com.manarah.evaluation.repo;

import com.manarah.evaluation.domain.TeacherEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeacherEvaluationRepository extends JpaRepository<TeacherEvaluation, Long> {
    List<TeacherEvaluation> findByTenantIdAndTeacherId(Long tenantId, Long teacherId);

    @Query("SELECT COALESCE(AVG(e.overall),0) FROM TeacherEvaluation e WHERE e.tenantId = :tenantId AND e.teacherId = :teacherId")
    double averageOverall(@Param("tenantId") Long tenantId, @Param("teacherId") Long teacherId);
}
