package com.manarah.course.repo;

import com.manarah.course.domain.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {
    List<CourseModule> findByTenantIdAndCourseIdOrderByPosition(Long tenantId, Long courseId);
}
