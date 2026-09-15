package com.manarah.course.repo;

import com.manarah.course.domain.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByTenantIdAndModuleIdOrderByPosition(Long tenantId, Long moduleId);
}
