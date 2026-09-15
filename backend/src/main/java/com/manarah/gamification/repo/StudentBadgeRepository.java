package com.manarah.gamification.repo;

import com.manarah.gamification.domain.StudentBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentBadgeRepository extends JpaRepository<StudentBadge, Long> {
    List<StudentBadge> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    boolean existsByTenantIdAndStudentIdAndBadgeId(Long tenantId, Long studentId, Long badgeId);
}
