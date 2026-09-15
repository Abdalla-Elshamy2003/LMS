package com.manarah.course.repo;

import com.manarah.course.domain.LessonMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonMaterialRepository extends JpaRepository<LessonMaterial, Long> {
    List<LessonMaterial> findByTenantIdAndLessonId(Long tenantId, Long lessonId);
    List<LessonMaterial> findByTenantIdAndType(Long tenantId, String type);
    Optional<LessonMaterial> findByTenantIdAndFileKey(Long tenantId, String fileKey);
}
