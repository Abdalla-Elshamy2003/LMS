package com.manarah.gamification.repo;

import com.manarah.gamification.domain.StudentPoints;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentPointsRepository extends JpaRepository<StudentPoints, Long> {
    Optional<StudentPoints> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    List<StudentPoints> findTop20ByTenantIdOrderByPointsDesc(Long tenantId);
}
