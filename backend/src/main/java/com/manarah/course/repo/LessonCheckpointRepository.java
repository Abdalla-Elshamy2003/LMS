package com.manarah.course.repo;

import com.manarah.course.domain.LessonCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonCheckpointRepository extends JpaRepository<LessonCheckpoint, Long> {

    List<LessonCheckpoint> findByTenantIdAndLessonIdOrderByPositionAsc(Long tenantId, Long lessonId);

    Optional<LessonCheckpoint> findByTenantIdAndId(Long tenantId, Long id);

    List<LessonCheckpoint> findByTenantIdAndQuestionId(Long tenantId, Long questionId);

    long countByTenantIdAndLessonId(Long tenantId, Long lessonId);
}
