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
    private final com.manarah.identity.repo.UserRepository users;
    private final com.manarah.academy.LinkedStudentAccounts linked;
    private final com.manarah.audit.AuditService audit;
    private final com.manarah.notification.NotificationService notifications;

    public EnrollmentService(EnrollmentRepository enrollments, StudyGroupRepository groups, CourseRepository courses,
                             StudentRepository students, ApplicationEventPublisher events, com.manarah.academy.AcademyAccess academyAccess,
                             com.manarah.course.LearningService learning, com.manarah.identity.repo.UserRepository users,
                             com.manarah.academy.LinkedStudentAccounts linked, com.manarah.audit.AuditService audit,
                             com.manarah.notification.NotificationService notifications) {
        this.users = users;
        this.linked = linked;
        this.audit = audit;
        this.notifications = notifications;
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
        academyAccess.rejectManagedAcademySelfService(tenantId);
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

    // ---- Courses students picked and haven't paid for yet (see CourseRequests) ----------------------------------

    public record PendingRequest(Long enrollmentId, Long courseId, String courseTitle, String year, BigDecimal price,
                                 Long studentId, String studentName, String studentCode, String email, String phone,
                                 String grade, java.time.Instant requestedAt) {}

    /** Every course request waiting for payment on the courses this person teaches (or manages), newest first. */
    public List<PendingRequest> pending(UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        List<PendingRequest> out = new java.util.ArrayList<>();
        for (Course c : courses.findByTenantId(tenantId)) {
            if (!canManage(actor, c.getId())) continue;
            for (Enrollment e : enrollments.findByTenantIdAndCourseId(tenantId, c.getId())) {
                if (!CourseRequests.WAITING.equals(e.getStatus())) continue;
                var s = students.findByTenantIdAndId(tenantId, e.getStudentId()).orElse(null);
                if (s == null) continue;
                // A student who joined from another teacher signs in with their own account: show that email.
                String email = s.getUserId() == null ? "" : users.findById(s.getUserId())
                        .map(u -> java.util.Objects.toString(linked.owner(u).getEmail(), "")).orElse("");
                out.add(new PendingRequest(e.getId(), c.getId(), c.getTitle(), java.util.Objects.toString(c.getGrade(), ""),
                        c.getFinalPrice(), s.getId(), s.getFullName(), s.getCode(), email, java.util.Objects.toString(s.getPhone(), ""),
                        java.util.Objects.toString(s.getGrade(), ""), e.getEnrolledAt()));
            }
        }
        out.sort(java.util.Comparator.comparing(PendingRequest::requestedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return out;
    }

    /** The teacher got the money: open the course for the student. */
    @Transactional
    public void activate(UserPrincipal actor, Long enrollmentId) {
        Enrollment e = waiting(actor, enrollmentId);
        e.setStatus("ACTIVE");
        enrollments.save(e);
        // Same as a completed checkout: a paying student is an active student, not a trial one.
        students.findByTenantIdAndId(e.getTenantId(), e.getStudentId())
                .filter(s -> java.util.Set.of("TRIAL", "PENDING_PAYMENT").contains(s.getStatus()))
                .ifPresent(s -> { s.setStatus("ACTIVE"); students.save(s); });
        Course c = courses.findByTenantIdAndId(e.getTenantId(), e.getCourseId()).orElseThrow();
        audit.record(actor, "COURSE_REQUEST_ACTIVATED", "Enrollment", e.getId(), CourseRequests.WAITING, "ACTIVE");
        events.publishEvent(new DomainEvents.EnrollmentCreated(e.getTenantId(), e.getStudentId(), c.getId(), c.getTitle()));
        tellStudent(e, "اتفتحلك كورس «" + c.getTitle() + "»", "المدرس أكّد اشتراكك. تقدر تبدأ المذاكرة دلوقتي من «كورساتي».");
    }

    /** The request was a mistake or never paid: drop it. The student can ask again later. */
    @Transactional
    public void reject(UserPrincipal actor, Long enrollmentId) {
        Enrollment e = waiting(actor, enrollmentId);
        Course c = courses.findByTenantIdAndId(e.getTenantId(), e.getCourseId()).orElseThrow();
        enrollments.delete(e);
        audit.record(actor, "COURSE_REQUEST_REJECTED", "Enrollment", e.getId(), CourseRequests.WAITING, null);
        tellStudent(e, "طلب اشتراكك في «" + c.getTitle() + "» اتلغى", "لو دفعت أو لسه عايز الكورس، تواصل مع المدرس.");
    }

    private Enrollment waiting(UserPrincipal actor, Long enrollmentId) {
        Long tenantId = TenantContext.require();
        Enrollment e = enrollments.findById(enrollmentId).filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("طلب الاشتراك", enrollmentId));
        assertCanManageCourse(actor, e.getCourseId());
        if (!CourseRequests.WAITING.equals(e.getStatus())) throw new ConflictException("الطلب ده اتعامل معاه قبل كده");
        return e;
    }

    private void tellStudent(Enrollment e, String title, String body) {
        students.findByTenantIdAndId(e.getTenantId(), e.getStudentId()).filter(s -> s.getUserId() != null).ifPresent(s ->
                notifications.notify(e.getTenantId(), new com.manarah.notification.NotificationDtos.NotifyCommand(
                        s.getUserId(), null, title, body, "ENROLLMENT", "Course", e.getCourseId(), List.of("IN_APP"))));
    }

    private boolean canManage(UserPrincipal actor, Long courseId) {
        try { assertCanManageCourse(actor, courseId); return true; }
        catch (com.manarah.common.exception.ApiExceptions.ApiException ex) { return false; }
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
