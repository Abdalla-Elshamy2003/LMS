package com.manarah.gamification.repo;

import com.manarah.gamification.domain.PointEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointEventRepository extends JpaRepository<PointEvent, Long> {
    List<PointEvent> findTop20ByTenantIdAndStudentIdOrderByCreatedAtDesc(Long tenantId, Long studentId);
}
