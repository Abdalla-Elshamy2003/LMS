package com.manarah.gradebook.repo;

import com.manarah.gradebook.domain.GradeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GradeItemRepository extends JpaRepository<GradeItem, Long> {

    List<GradeItem> findByTenantIdAndStudentId(Long tenantId, Long studentId);

    List<GradeItem> findByTenantIdAndStudentIdAndCourseId(Long tenantId, Long studentId, Long courseId);

    Optional<GradeItem> findByTenantIdAndId(Long tenantId, Long id);

    Optional<GradeItem> findByTenantIdAndSourceTypeAndSourceIdAndStudentId(
            Long tenantId, String sourceType, Long sourceId, Long studentId);

    @Query("""
            SELECT COALESCE(AVG(CASE WHEN g.maxScore > 0 THEN g.score / g.maxScore * 100 ELSE 0 END), 0)
            FROM GradeItem g WHERE g.tenantId = :tenantId AND g.studentId = :studentId
            """)
    double averagePercent(@Param("tenantId") Long tenantId, @Param("studentId") Long studentId);
}
