package com.manarah.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanAccessCodeRepository extends JpaRepository<PlanAccessCode, Long> {
    Optional<PlanAccessCode> findByCodeIgnoreCase(String code);
    List<PlanAccessCode> findByPlanIdOrderByIdDesc(Long planId);
    boolean existsByCode(String code);
}
