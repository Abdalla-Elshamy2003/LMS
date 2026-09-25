package com.manarah.analytics;

import com.manarah.attendance.repo.AttendanceRecordRepository;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.gradebook.repo.GradeItemRepository;
import com.manarah.homework.repo.SubmissionRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.payment.repo.InvoiceRepository;
import com.manarah.student.domain.AcademicStatus;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregation queries powering role dashboards (§16, §20). */
@Service
public class AnalyticsService {

    private final StudentRepository students;
    private final UserRepository users;
    private final CourseRepository courses;
    private final com.manarah.academy.TeacherAcademyRepository academies;
    private final InvoiceRepository invoices;
    private final AttendanceRecordRepository attendance;
    private final SubmissionRepository submissions;
    private final GradeItemRepository grades;
    private final EnrollmentRepository enrollments;

    public AnalyticsService(StudentRepository students, UserRepository users, CourseRepository courses,
                            InvoiceRepository invoices, AttendanceRecordRepository attendance,
                            SubmissionRepository submissions, GradeItemRepository grades,
                            EnrollmentRepository enrollments, com.manarah.academy.TeacherAcademyRepository academies) {
        this.academies = academies;
        this.students = students;
        this.users = users;
        this.courses = courses;
        this.invoices = invoices;
        this.attendance = attendance;
        this.submissions = submissions;
        this.grades = grades;
        this.enrollments = enrollments;
    }

    public Map<String, Object> adminDashboard() {
        Long tenantId = TenantContext.require();
        List<Student> all = students.findByTenantId(tenantId);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("students", all.size());
        kpis.put("activeStudents", all.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count());
        // Teacher and course counts span the academies this tenant manages, matching what the
        // staff and courses pages now list. Student and money figures deliberately stay scoped to
        // this tenant: each academy keeps its own roster and billing, and summing them here would
        // make the averages below (computed over this tenant's students) disagree with the totals.
        var visibleTenants = academies.visibleTenantIds(tenantId);
        kpis.put("teachers", users.countByTenantIdInAndRole(visibleTenants, Role.TEACHER));
        kpis.put("courses", courses.countByTenantIdIn(visibleTenants));
        kpis.put("avgOverall", round1(students.averageOverall(tenantId)));
        kpis.put("collected", invoices.totalCollected(tenantId));
        kpis.put("outstanding", invoices.totalOutstanding(tenantId));

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (AcademicStatus st : AcademicStatus.values()) {
            distribution.put(st.name(), students.countByTenantIdAndAcademicStatus(tenantId, st.name()));
        }

        List<Map<String, Object>> top = all.stream()
                .sorted(Comparator.comparingDouble(Student::getOverallPercent).reversed())
                .limit(6).map(this::miniStudent).toList();

        List<Map<String, Object>> atRisk = all.stream()
                .filter(s -> "AT_RISK".equals(s.getAcademicStatus()) || "NEEDS_ATTENTION".equals(s.getAcademicStatus()))
                .sorted(Comparator.comparingDouble(Student::getOverallPercent))
                .limit(8).map(this::miniStudent).toList();

        return Map.of(
                "kpis", kpis,
                "academicDistribution", distribution,
                "topStudents", top,
                "atRiskStudents", atRisk);
    }

    public Map<String, Object> teacherDashboard(Long teacherId) {
        Long tenantId = TenantContext.require();
        var myCourses = courses.findByTenantIdAndTeacherId(tenantId, teacherId);
        long studentCount = myCourses.stream()
                .mapToLong(c -> enrollments.countStudying(tenantId, c.getId())).sum();
        return Map.of(
                "courses", myCourses.size(),
                "students", studentCount,
                "myCourses", myCourses.stream().map(c -> Map.of(
                        "id", c.getId(), "title", c.getTitle(),
                        "students", enrollments.countStudying(tenantId, c.getId()))).toList());
    }

    public Map<String, Object> studentDashboard(Long studentId) {
        Long tenantId = TenantContext.require();
        Student s = students.findByTenantIdAndId(tenantId, studentId).orElse(null);
        if (s == null) return Map.of();
        List<Student> all = students.findByTenantId(tenantId);
        List<Student> ranked = all.stream()
                .sorted(Comparator.comparingDouble(Student::getOverallPercent).reversed()).toList();
        int rank = ranked.indexOf(s) + 1;

        // Everything offered for the year this student signed up for, whether or not they're
        // enrolled yet — that catalogue is the starting point of their dashboard.
        var enrolledIds = enrollments.findByTenantIdAndStudentId(tenantId, studentId)
                .stream().map(e -> e.getCourseId()).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> yearCourses = s.getGrade() == null ? List.of()
                : courses.findByTenantIdAndGradeAndStatus(tenantId, s.getGrade(), "ACTIVE").stream()
                    .map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", c.getId());
                        m.put("title", c.getTitle());
                        m.put("subject", c.getSubject() == null ? "" : c.getSubject());
                        m.put("price", c.getPrice());
                        m.put("finalPrice", c.getFinalPrice());
                        m.put("discountPercent", c.getDiscountPercent() == null ? 0 : c.getDiscountPercent());
                        m.put("coverUrl", c.getCoverUrl() == null ? "" : c.getCoverUrl());
                        m.put("enrolled", enrolledIds.contains(c.getId()));
                        return m;
                    }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attendanceRate", s.getAttendanceRate());
        out.put("avgScore", s.getAvgScore());
        out.put("homeworkRate", s.getHomeworkRate());
        out.put("overallPercent", s.getOverallPercent());
        out.put("academicStatus", s.getAcademicStatus());
        out.put("rank", rank);
        out.put("totalStudents", all.size());
        out.put("courses", enrolledIds.size());
        out.put("grade", s.getGrade() == null ? "" : s.getGrade());
        out.put("yearCourses", yearCourses);
        return out;
    }

    private Map<String, Object> miniStudent(Student s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getFullName());
        m.put("code", s.getCode());
        m.put("overallPercent", s.getOverallPercent());
        m.put("attendanceRate", s.getAttendanceRate());
        m.put("academicStatus", s.getAcademicStatus());
        return m;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
