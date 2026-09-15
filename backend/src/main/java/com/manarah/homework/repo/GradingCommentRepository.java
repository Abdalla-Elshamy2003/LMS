package com.manarah.homework.repo;

import com.manarah.homework.domain.GradingComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradingCommentRepository extends JpaRepository<GradingComment, Long> {
    List<GradingComment> findByTenantIdAndTeacherIdOrderByUsesDescCreatedAtDesc(Long tenantId, Long teacherId);
    Optional<GradingComment> findByTenantIdAndIdAndTeacherId(Long tenantId, Long id, Long teacherId);
    long countByTenantIdAndTeacherId(Long tenantId, Long teacherId);
}
