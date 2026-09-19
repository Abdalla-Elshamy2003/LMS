package com.manarah.student;

import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The courses a student is currently in, in the small shape a scan result needs: what they chose,
 * which school year it is for, and its weekly time. Shared by the public QR verification page and the
 * staff gate scan so both answer "which course is this student here for?" the same way.
 */
@Component
public class StudentCourseSummaries {

    /** A trial enrollment is a paid course the student picked but has not paid for yet; it still counts as "chosen". */
    private static final Set<String> CURRENT = Set.of("ACTIVE", "TRIAL");

    public record Line(String title, String year, String schedule) {}

    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;

    public StudentCourseSummaries(EnrollmentRepository enrollments, CourseRepository courses) {
        this.enrollments = enrollments;
        this.courses = courses;
    }

    public List<Line> forStudent(Long tenantId, Long studentId) {
        return enrollments.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(e -> CURRENT.contains(e.getStatus()))
                .map(e -> courses.findByTenantIdAndId(tenantId, e.getCourseId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(c -> new Line(c.getTitle(), blankToNull(c.getGrade()), blankToNull(c.getSchedule())))
                .toList();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
