package com.manarah.enrollment;

import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.course.domain.Course;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.notification.NotificationDtos.NotifyCommand;
import com.manarah.notification.NotificationService;
import com.manarah.student.repo.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * A student asking for a course — at registration, when joining a teacher, or from their dashboard. A free course
 * opens straight away. A paid one is sold with its year and subject: asking for it asks to subscribe to that
 * teacher's plan for the year ({@link com.manarah.subscription.PlanAccess}), which waits for payment and then opens
 * every course of the year and subject for the plan's months. ({@code TRIAL} rows — a single course waiting for
 * payment — come from before plans and are still honoured.) Nothing waiting grants access anywhere.
 *
 * <p>Idempotent, and never overrides the teacher: asking again for a course already open or already waiting changes
 * nothing, and a course the teacher closed for this student ({@code INACTIVE}) stays closed.
 */
@Service
public class CourseRequests {
    public static final String WAITING = "TRIAL";

    private final EnrollmentRepository enrollments;
    private final TeacherAcademyRepository academies;
    private final StudentRepository students;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final com.manarah.subscription.PlanAccess plans;

    public CourseRequests(EnrollmentRepository enrollments, TeacherAcademyRepository academies, StudentRepository students,
                          NotificationService notifications, ApplicationEventPublisher events,
                          com.manarah.subscription.PlanAccess plans) {
        this.enrollments = enrollments; this.academies = academies; this.students = students;
        this.notifications = notifications; this.events = events; this.plans = plans;
    }

    /** What the student sees for a course: ACTIVE, COMPLETED, PENDING (waiting for payment), EXPIRED (their subscription ran out), CLOSED or NONE. */
    public static String stateOf(Enrollment e) {
        if (e == null) return "NONE";
        return switch (e.getStatus()) {
            case "ACTIVE" -> "ACTIVE";
            case "COMPLETED" -> "COMPLETED";
            case WAITING -> "PENDING";
            case com.manarah.subscription.PlanAccess.LOCKED -> "EXPIRED";
            default -> "CLOSED";
        };
    }

    public static boolean isFree(Course c) {
        BigDecimal price = c.getFinalPrice() != null ? c.getFinalPrice() : c.getPrice();
        return price == null || price.signum() <= 0;
    }

    @Transactional
    public String request(Long tenantId, Long studentId, Course course) {
        if (!tenantId.equals(course.getTenantId()) || !"ACTIVE".equals(course.getStatus()))
            throw new BadRequestException("الكورس ده مش متاح للاشتراك دلوقتي");
        var existing = enrollments.findByTenantIdAndStudentIdAndCourseId(tenantId, studentId, course.getId()).orElse(null);
        if (existing != null) {
            String state = stateOf(existing);
            if ("CLOSED".equals(state)) throw new ForbiddenException("اشتراكك في الكورس ده موقوف عند المدرس. تواصل معاه.");
            if (!"EXPIRED".equals(state)) return state;
        }
        if (existing == null && isFree(course)) return requestFree(tenantId, studentId, course);
        // Paid (or its subscription ran out): subscribe to the teacher's plan for the course's year and subject.
        String grade = students.findByTenantIdAndId(tenantId, studentId).map(s -> s.getGrade()).orElse(null);
        var plan = plans.planFor(course, grade);
        if (plans.running(plan.getId(), studentId, java.time.Instant.now())) {
            plans.open(plan, studentId);
            return stateOf(enrollments.findByTenantIdAndStudentIdAndCourseId(tenantId, studentId, course.getId()).orElse(null));
        }
        plans.request(plan, studentId);
        return "PENDING";
    }

    private String requestFree(Long tenantId, Long studentId, Course course) {
        // An unpublished teacher space is invite-only: even a free course waits for the teacher there.
        boolean open = academies.findByTenantId(tenantId).map(TeacherAcademy::isPublished).orElse(true);
        Enrollment e = new Enrollment();
        e.setTenantId(tenantId);
        e.setStudentId(studentId);
        e.setCourseId(course.getId());
        e.setStatus(open ? "ACTIVE" : WAITING);
        enrollments.save(e);
        if (open) events.publishEvent(new DomainEvents.EnrollmentCreated(tenantId, studentId, course.getId(), course.getTitle()));
        else tellTeacher(tenantId, studentId, course);
        return stateOf(e);
    }

    private void tellTeacher(Long tenantId, Long studentId, Course course) {
        if (course.getTeacherId() == null) return;
        String name = students.findByTenantIdAndId(tenantId, studentId).map(s -> s.getFullName()).orElse("طالب");
        notifications.notify(tenantId, new NotifyCommand(course.getTeacherId(), null, "طلب اشتراك جديد",
                name + " اختار كورس «" + course.getTitle() + "» ومستني يدفع. أول ما تستلم الفلوس فعّله من «طلبات الاشتراك» في لوحتك أو ابعتله كود.",
                "ENROLLMENT", "Course", course.getId(), List.of("IN_APP")));
    }
}
