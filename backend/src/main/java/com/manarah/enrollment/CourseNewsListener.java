package com.manarah.enrollment;

import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.common.SchoolYears;
import com.manarah.common.events.DomainEvents;
import com.manarah.course.repo.CourseRepository;
import com.manarah.course.repo.LessonRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.notification.NotificationDtos.NotifyCommand;
import com.manarah.notification.NotificationService;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells students when their teacher puts up something for them: a new course for their school year (they can then
 * subscribe to it from their dashboard), or a new lesson in a course they study. Runs after the teacher's change has
 * committed and off the request thread, so a slow inbox never slows the teacher down.
 */
@Component
public class CourseNewsListener {
    private static final Logger log = LoggerFactory.getLogger(CourseNewsListener.class);
    /** A teacher building a course adds lessons back to back; one heads-up per course in this window is enough. */
    private static final Duration LESSON_QUIET = Duration.ofMinutes(30);

    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final TeacherAcademyRepository academies;
    private final NotificationService notifications;
    private final Map<Long, Instant> lastLessonNews = new ConcurrentHashMap<>();

    public CourseNewsListener(CourseRepository courses, LessonRepository lessons, StudentRepository students,
                              EnrollmentRepository enrollments, TeacherAcademyRepository academies, NotificationService notifications) {
        this.courses = courses; this.lessons = lessons; this.students = students;
        this.enrollments = enrollments; this.academies = academies; this.notifications = notifications;
    }

    @Async
    @TransactionalEventListener
    public void onCourseOffered(DomainEvents.CourseOffered e) {
        try {
            var c = courses.findByTenantIdAndId(e.tenantId(), e.courseId()).filter(x -> "ACTIVE".equals(x.getStatus())).orElse(null);
            if (c == null) return;
            String year = c.getGrade() == null ? "" : c.getGrade().trim();
            String teacher = academies.findByTenantId(e.tenantId()).map(TeacherAcademy::getName).orElse("مدرسك");
            for (Student s : students.findByTenantId(e.tenantId())) {
                if (s.getUserId() == null || "ARCHIVED".equals(s.getStatus())) continue;
                // A course for one year goes to that year only; a course for every year goes to everyone.
                if (!year.isEmpty() && !SchoolYears.same(s.getGrade(), year)) continue;
                tell(e.tenantId(), s.getUserId(), year.isEmpty() ? "كورس جديد" : "كورس جديد لـ" + year,
                        teacher + " نزّل «" + c.getTitle() + "». هتلاقيه في «كورساتي» وتقدر تشترك فيه من هناك.", c.getId());
            }
        } catch (RuntimeException ex) {
            log.warn("course_news outcome=error kind=course courseId={} cause={}", e.courseId(), ex.toString());
        }
    }

    @Async
    @TransactionalEventListener
    public void onLessonAdded(DomainEvents.LessonAdded e) {
        Instant now = Instant.now();
        Instant last = lastLessonNews.put(e.courseId(), now);
        if (last != null && last.isAfter(now.minus(LESSON_QUIET))) return;
        try {
            var c = courses.findByTenantIdAndId(e.tenantId(), e.courseId()).orElse(null);
            var lesson = lessons.findById(e.lessonId()).orElse(null);
            if (c == null || lesson == null) return;
            for (var enr : enrollments.findByTenantIdAndCourseId(e.tenantId(), c.getId())) {
                if (!EnrollmentRepository.STUDYING.contains(enr.getStatus())) continue;
                students.findByTenantIdAndId(e.tenantId(), enr.getStudentId()).filter(s -> s.getUserId() != null).ifPresent(s ->
                        tell(e.tenantId(), s.getUserId(), "درس جديد في «" + c.getTitle() + "»", lesson.getTitle(), c.getId()));
            }
        } catch (RuntimeException ex) {
            log.warn("course_news outcome=error kind=lesson courseId={} cause={}", e.courseId(), ex.toString());
        }
    }

    private void tell(Long tenantId, Long userId, String title, String body, Long courseId) {
        notifications.notify(tenantId, new NotifyCommand(userId, null, title, body, "COURSE", "Course", courseId, List.of("IN_APP")));
    }
}
