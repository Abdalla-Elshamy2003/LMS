package com.manarah.course.repo;

import com.manarah.course.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every listing here leaves out a course head office deleted ({@link Course#DELETED}), so it drops out of the teacher's,
 * the admin's and the student's screens in one place. Lookups by id still find it: enrollments, payments and
 * certificates keep pointing at it, and their history must keep resolving its title.
 */
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTenantIdAndStatusNot(Long tenantId, String status);
    List<Course> findByTenantIdInAndStatusNot(List<Long> tenantIds, String status);
    List<Course> findByTenantIdAndTeacherIdAndStatusNot(Long tenantId, Long teacherId, String status);
    long countByTenantIdAndStatusNot(Long tenantId, String status);
    long countByTenantIdInAndStatusNot(List<Long> tenantIds, String status);

    default List<Course> findByTenantId(Long tenantId) {
        return findByTenantIdAndStatusNot(tenantId, Course.DELETED);
    }

    /** Rolls up managed academy tenants alongside the caller's own — see AcademyAccess.visibleTenantIds. */
    default List<Course> findByTenantIdIn(List<Long> tenantIds) {
        return findByTenantIdInAndStatusNot(tenantIds, Course.DELETED);
    }

    default List<Course> findByTenantIdAndTeacherId(Long tenantId, Long teacherId) {
        return findByTenantIdAndTeacherIdAndStatusNot(tenantId, teacherId, Course.DELETED);
    }

    /** The catalogue for one school year — what a student sees on their dashboard. */
    List<Course> findByTenantIdAndGradeAndStatus(Long tenantId, String grade, String status);
    Optional<Course> findByTenantIdAndId(Long tenantId, Long id);

    default long countByTenantId(Long tenantId) {
        return countByTenantIdAndStatusNot(tenantId, Course.DELETED);
    }

    default long countByTenantIdIn(List<Long> tenantIds) {
        return countByTenantIdInAndStatusNot(tenantIds, Course.DELETED);
    }
}
