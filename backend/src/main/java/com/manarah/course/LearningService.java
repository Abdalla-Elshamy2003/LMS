package com.manarah.course;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.*;
import com.manarah.course.repo.*;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import com.manarah.student.repo.StudentRepository;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class LearningService {
    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonProgressRepository progress;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final CourseService content;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository studentGuardians;
    private final com.manarah.academy.TeacherAcademyRepository academies;

    public LearningService(CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons,
            LessonProgressRepository progress, EnrollmentRepository enrollments, StudentRepository students, CourseService content,
            GuardianRepository guardians, StudentGuardianRepository studentGuardians,
            com.manarah.academy.TeacherAcademyRepository academies) {
        this.courses = courses; this.modules = modules; this.lessons = lessons; this.progress = progress;
        this.enrollments = enrollments; this.students = students; this.content = content;
        this.guardians = guardians; this.studentGuardians = studentGuardians;
        this.academies = academies;
    }

    public Course access(UserPrincipal actor, Long courseId, boolean write) {
        Course c = courses.findByTenantIdAndId(actor.getTenantId(), courseId)
                .orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        if (actor.isAdmin() || actor.getRole() == Role.CONTENT_MANAGER) return c;
        if (actor.getRole() == Role.TEACHER && Objects.equals(c.getTeacherId(), actor.getId())) return c;
        if (!write && actor.getRole() == Role.ASSISTANT) return c;
        if (!write && actor.getRole() == Role.PARENT) {
            var guardian = guardians.findByTenantIdAndUserId(actor.getTenantId(), actor.getId());
            if (guardian.isPresent() && studentGuardians.findByTenantIdAndGuardianId(actor.getTenantId(), guardian.get().getId()).stream()
                    .anyMatch(link -> enrollments.findByTenantIdAndStudentIdAndCourseId(actor.getTenantId(), link.getStudentId(), courseId)
                            .map(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).orElse(false))) return c;
        }
        if (!write && actor.getRole() == Role.STUDENT) {
            var enrollment = enrollments.findByTenantIdAndStudentIdAndCourseId(actor.getTenantId(), studentId(actor), courseId);
            if (enrollment.isPresent() && Set.of("ACTIVE", "COMPLETED").contains(enrollment.get().getStatus())) return c;
        }
        throw new ForbiddenException("هذا المحتوى متاح للطلاب المسجلين ومدرس الكورس والإدارة فقط");
    }

    public Long moduleCourse(UserPrincipal actor, Long moduleId) {
        return modules.findById(moduleId).filter(m -> m.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("الفصل", moduleId)).getCourseId();
    }

    public Long lessonCourse(UserPrincipal actor, Long lessonId) {
        var lesson = lessons.findById(lessonId).filter(l -> l.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("الدرس", lessonId));
        return moduleCourse(actor, lesson.getModuleId());
    }

    public void requireReleasedLesson(UserPrincipal actor, Long lessonId) {
        var lesson = lessons.findById(lessonId).filter(l -> l.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> new ForbiddenException("الدرس غير متاح"));
        if ((actor.getRole() == Role.STUDENT || actor.getRole() == Role.PARENT)
                && lesson.getReleaseAt() != null && lesson.getReleaseAt().isAfter(java.time.Instant.now()))
            throw new ForbiddenException("لم يحن موعد فتح هذا الدرس");
    }

    private Long studentId(UserPrincipal actor) {
        return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .orElseThrow(() -> new ForbiddenException("لا يوجد ملف طالب مرتبط بالحساب")).getId();
    }

    public record ProgressView(Long lessonId, boolean completed, int lastPosition, Instant updatedAt) {}
    public record LearnerView(Long studentId, String name, long completed, long started, Instant lastActivity) {}
    public record LearningCourse(CourseDtos.CourseSummary summary, int lessonCount, long videoCount, long fileCount,
            long completedCount, Long nextLessonId, String nextLessonTitle) {}

    public List<LearningCourse> library(UserPrincipal actor) {
        List<Course> available;
        List<LessonProgress> own = List.of();
        if (actor.getRole() == Role.STUDENT) {
            Long sid = studentId(actor);
            Set<Long> ids = new HashSet<>();
            enrollments.findByTenantIdAndStudentId(actor.getTenantId(), sid).stream()
                    .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).forEach(e -> ids.add(e.getCourseId()));
            available = courses.findByTenantId(actor.getTenantId()).stream().filter(c -> ids.contains(c.getId())).toList();
            own = progress.findByTenantIdAndStudentId(actor.getTenantId(), sid);
        } else if (actor.getRole() == Role.TEACHER) {
            available = courses.findByTenantIdAndTeacherId(actor.getTenantId(), actor.getId());
        } else if (actor.isAdmin()) {
            available = courses.findByTenantIdIn(academies.visibleTenantIds(actor.getTenantId()));
        } else if (actor.getRole() == Role.CONTENT_MANAGER) {
            available = courses.findByTenantId(actor.getTenantId());
        } else throw new ForbiddenException("غير مسموح");
        Set<Long> done = new HashSet<>();
        own.stream().filter(LessonProgress::isCompleted).forEach(p -> done.add(p.getLessonId()));
        return available.stream().map(c -> {
            var detail = content.getForTenant(c.getTenantId(), c.getId(), actor.getRole() != Role.STUDENT);
            var ls = detail.modules().stream().flatMap(m -> m.lessons().stream()).toList();
            var mats = ls.stream().flatMap(l -> l.materials().stream()).toList();
            var next = ls.stream().filter(l -> !done.contains(l.id())).findFirst().orElse(null);
            return new LearningCourse(detail.summary(), ls.size(), mats.stream().filter(m -> "VIDEO".equals(m.type())).count(),
                    mats.stream().filter(m -> !"VIDEO".equals(m.type())).count(), ls.stream().filter(l -> done.contains(l.id())).count(),
                    next == null ? null : next.id(), next == null ? null : next.title());
        }).toList();
    }

    public List<ProgressView> ownProgress(UserPrincipal actor, Long courseId) {
        access(actor, courseId, false);
        var ids = lessonIds(courseId);
        return progress.findByTenantIdAndStudentId(actor.getTenantId(), studentId(actor)).stream()
                .filter(p -> ids.contains(p.getLessonId())).map(this::view).toList();
    }

    private Set<Long> lessonIds(Long courseId) {
        Set<Long> ids = new HashSet<>();
        content.get(courseId, true).modules().forEach(m -> m.lessons().forEach(l -> ids.add(l.id())));
        return ids;
    }

    @Transactional
    public ProgressView save(UserPrincipal actor, Long lessonId, int position, boolean completed) {
        access(actor, lessonCourse(actor, lessonId), false);
        Long sid = studentId(actor);
        var p = progress.findByTenantIdAndLessonIdAndStudentId(actor.getTenantId(), lessonId, sid).orElseGet(() -> {
            var fresh = new LessonProgress(); fresh.setTenantId(actor.getTenantId()); fresh.setLessonId(lessonId);
            fresh.setStudentId(sid); fresh.setOpenedAt(Instant.now()); fresh.setViews(1); return fresh;
        });
        p.setLastPosition(position);
        p.setWatchedSeconds(Math.max(p.getWatchedSeconds(), position));
        if (completed && !p.isCompleted()) { p.setCompleted(true); p.setCompletedAt(Instant.now()); }
        p.setUpdatedAt(Instant.now());
        return view(progress.save(p));
    }

    public List<LearnerView> learners(UserPrincipal actor, Long courseId) {
        access(actor, courseId, true);
        var ids = lessonIds(courseId);
        return enrollments.findByTenantIdAndCourseId(actor.getTenantId(), courseId).stream().map(e -> {
            var rows = progress.findByTenantIdAndStudentId(actor.getTenantId(), e.getStudentId()).stream()
                    .filter(p -> ids.contains(p.getLessonId())).toList();
            String name = students.findById(e.getStudentId()).filter(s -> s.getTenantId().equals(actor.getTenantId()))
                    .map(s -> s.getFullName()).orElse("طالب");
            return new LearnerView(e.getStudentId(), name, rows.stream().filter(LessonProgress::isCompleted).count(), rows.size(),
                    rows.stream().map(LessonProgress::getUpdatedAt).max(Comparator.naturalOrder()).orElse(null));
        }).toList();
    }

    private ProgressView view(LessonProgress p) { return new ProgressView(p.getLessonId(), p.isCompleted(), p.getLastPosition(), p.getUpdatedAt()); }
}
