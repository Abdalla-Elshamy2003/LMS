package com.manarah.course.repo;

import com.manarah.course.domain.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    Optional<LessonProgress> findByTenantIdAndLessonIdAndStudentId(Long tenantId, Long lessonId, Long studentId);
    List<LessonProgress> findByTenantIdAndStudentId(Long tenantId, Long studentId);
}
