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
 * opens straight away; a paid one is recorded as {@code TRIAL}, i.e. "picked, waiting for payment": it shows on the
 * student's dashboard with the teacher's payment details and opens only once the teacher's code is redeemed or the
 * teacher activates it. A TRIAL row grants no access anywhere — learning, exams and homework all require ACTIVE.
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

    public CourseRequests(EnrollmentRepository enrollments, TeacherAcademyRepository academies, StudentRepository students,
                          NotificationService notifications, ApplicationEventPublisher events) {
        this.enrollments = enrollments; this.academies = academies; this.students = students;
        this.notifications = notifications; this.events = events;
    }

    /** What the student sees for a course: ACTIVE, COMPLETED, PENDING (waiting for payment), CLOSED or NONE. */
    public static String stateOf(Enrollment e) {
        if (e == null) return "NONE";
        return switch (e.getStatus()) {
            case "ACTIVE" -> "ACTIVE";
            case "COMPLETED" -> "COMPLETED";
            case WAITING -> "PENDING";
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
            return state;
        }
        // An unpublished teacher space is invite-only: even a free course waits for the teacher there.
        boolean open = isFree(course) && academies.findByTenantId(tenantId).map(TeacherAcademy::isPublished).orElse(true);
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
