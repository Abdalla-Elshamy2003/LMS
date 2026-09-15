package com.manarah.risk.repo;

import com.manarah.risk.domain.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findFirstByTenantIdAndStudentIdOrderByAssessedAtDesc(Long tenantId, Long studentId);
    List<RiskAssessment> findByTenantIdAndStudentIdOrderByAssessedAtDesc(Long tenantId, Long studentId);
}
