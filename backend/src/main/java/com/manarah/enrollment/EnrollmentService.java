package com.manarah.enrollment;

import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.domain.StudyGroup;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.enrollment.repo.StudyGroupRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.manarah.security.UserPrincipal;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollments;
    private final StudyGroupRepository groups;
    private final CourseRepository courses;
    private final StudentRepository students;
    private final ApplicationEventPublisher events;
    private final com.manarah.academy.AcademyAccess academyAccess;
    private final com.manarah.course.LearningService learning;

    public EnrollmentService(EnrollmentRepository enrollments, StudyGroupRepository groups, CourseRepository courses,
                             StudentRepository students, ApplicationEventPublisher events, com.manarah.academy.AcademyAccess academyAccess,
                             com.manarah.course.LearningService learning) {
        this.academyAccess = academyAccess;
        this.enrollments = enrollments;
        this.groups = groups;
        this.courses = courses;
        this.students = students;
        this.events = events;
        this.learning = learning;
    }

    /** A student enrolling themselves for free — refuses paid courses outright, directing them
     *  to checkout instead, so a course price can never be bypassed by calling this endpoint
     *  directly. Staff enrolling a student manually (the plain {@link #enroll} below) is exempt:
     *  that's an administrative action which implies payment was already collected some other way. */
    @Transactional
    public Enrollment selfEnroll(Long studentId, Long courseId) {
        Long tenantId = TenantContext.require();
        academyAccess.rejectSelfEnrollment(tenantId);
        Course course = courses.findByTenantIdAndId(tenantId, courseId)
                .orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        if (course.getPrice() != null && course.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            throw new ForbiddenException("هذا الكورس مدفوع — أكمل عملية الدفع أولاً للالتحاق به");
        }
        return enrollInternal(studentId, courseId, null);
    }

    @Transactional
    public Enrollment enroll(UserPrincipal actor, Long studentId, Long courseId, Long groupId) {
        assertCanManageCourse(actor, courseId);
        return enrollInternal(studentId, courseId, groupId);
    }

    private Enrollment enrollInternal(Long studentId, Long courseId, Long groupId) {
        Long tenantId = TenantContext.require();
        var course = courses.findByTenantIdAndId(tenantId, courseId)
                .orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        students.findByTenantIdAndId(tenantId, studentId).orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        if (enrollments.existsByTenantIdAndStudentIdAndCourseId(tenantId, studentId, courseId)) {
            throw new ConflictException("الطالب مسجل بالفعل في هذا الكورس");
        }
        Enrollment e = new Enrollment();
        e.setTenantId(tenantId);
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setGroupId(groupId);
        enrollments.save(e);
        events.publishEvent(new DomainEvents.EnrollmentCreated(tenantId, studentId, courseId, course.getTitle()));
        return e;
    }

    public void assertCanManageCourse(UserPrincipal actor, Long courseId) {
        learning.access(actor, courseId, true);
    }

    public List<Map<String, Object>> byCourse(Long courseId) {
        Long tenantId = TenantContext.require();
        return enrollments.findByTenantIdAndCourseId(tenantId, courseId).stream()
                .map(e -> {
                    var s = students.findByTenantIdAndId(tenantId, e.getStudentId()).orElse(null);
                    return Map.<String, Object>of(
                            "enrollmentId", e.getId(),
                            "studentId", e.getStudentId(),
                            "studentName", s == null ? "—" : s.getFullName(),
                            "code", s == null ? "" : s.getCode(),
                            "status", e.getStatus());
                }).toList();
    }

    @Transactional
    public StudyGroup createGroup(Long courseId, String name, String schedule, Long teacherId, Long roomId, int capacity) {
        Long tenantId = TenantContext.require();
        StudyGroup g = new StudyGroup();
        g.setTenantId(tenantId);
        g.setCourseId(courseId);
        g.setName(name);
        g.setScheduleText(schedule);
        g.setTeacherId(teacherId);
        g.setRoomId(roomId);
        g.setCapacity(capacity);
        groups.save(g);
        return g;
    }

    public List<StudyGroup> groupsForCourse(Long courseId) {
        return groups.findByTenantIdAndCourseId(TenantContext.require(), courseId);
    }
}
