package com.manarah.course;

import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.CourseDtos.*;
import com.manarah.course.domain.*;
import com.manarah.course.repo.*;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonMaterialRepository materials;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final com.manarah.academy.TeacherAcademyRepository academies;
    private final LessonProgressRepository progress;
    private final VideoWatchSessionRepository watchSessions;
    private final LessonCheckpointRepository checkpoints;
    private final LessonCheckpointAnswerRepository checkpointAnswers;
    private final com.manarah.academy.TeacherScope teacherScope;
    private final org.springframework.context.ApplicationEventPublisher events;

    public CourseService(CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons,
                         LessonMaterialRepository materials, EnrollmentRepository enrollments, UserRepository users,
                         com.manarah.academy.TeacherAcademyRepository academies, LessonProgressRepository progress,
                         VideoWatchSessionRepository watchSessions, LessonCheckpointRepository checkpoints,
                         LessonCheckpointAnswerRepository checkpointAnswers,
                         com.manarah.academy.TeacherScope teacherScope,
                         org.springframework.context.ApplicationEventPublisher events) {
        this.teacherScope = teacherScope;
        this.events = events;
        this.progress = progress;
        this.watchSessions = watchSessions;
        this.checkpoints = checkpoints;
        this.checkpointAnswers = checkpointAnswers;
        this.courses = courses;
        this.modules = modules;
        this.lessons = lessons;
        this.materials = materials;
        this.enrollments = enrollments;
        this.users = users;
        this.academies = academies;
    }

    /** Spans the caller's own tenant plus every academy tenant they manage, so courses created
     *  inside a teacher's own space still appear on the main courses page. */
    public List<CourseSummary> list() {
        return courses.findByTenantIdIn(academies.visibleTenantIds(TenantContext.require()))
                .stream().map(c -> toSummary(c.getTenantId(), c)).toList();
    }

    public List<CourseSummary> forTeacher(Long teacherId) {
        Long tenantId = TenantContext.require();
        return courses.findByTenantIdAndTeacherId(tenantId, teacherId).stream().map(c -> toSummary(tenantId, c)).toList();
    }

    /** {@code includeUnreleased} lets staff/authoring views see lessons whose {@code releaseAt}
     *  is still in the future; students/parents never do — those lessons are dropped entirely
     *  rather than shown locked, so a scheduled lesson simply doesn't exist yet from their side. */
    public CourseDetail get(Long id, boolean includeUnreleased) {
        return getForTenant(TenantContext.require(), id, includeUnreleased);
    }

    // Package-private: callers must first derive tenantId from their authorized course list.
    CourseDetail getForTenant(Long tenantId, Long id, boolean includeUnreleased) {
        Course c = courses.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الكورس", id));
        Instant now = Instant.now();
        List<ModuleView> moduleViews = modules.findByTenantIdAndCourseIdOrderByPosition(tenantId, id).stream()
                .map(m -> {
                    List<LessonView> lessonViews = lessons.findByTenantIdAndModuleIdOrderByPosition(tenantId, m.getId()).stream()
                            .filter(l -> includeUnreleased || l.getReleaseAt() == null || !l.getReleaseAt().isAfter(now))
                            .map(l -> {
                                List<MaterialView> mats = materials.findByTenantIdAndLessonId(tenantId, l.getId()).stream()
                                        .map(this::toMaterial).toList();
                                return new LessonView(l.getId(), l.getTitle(), l.getPosition(), l.getDurationMin(),
                                        l.getContentText(), mats, l.getCreatedAt(), l.getReleaseAt(), l.getAiSummary());
                            }).toList();
                    return new ModuleView(m.getId(), m.getTitle(), m.getPosition(), lessonViews, m.getCreatedAt());
                }).toList();
        return new CourseDetail(toSummary(tenantId, c), c.getDescription(), moduleViews);
    }

    @Transactional
    public CourseDetail create(UserPrincipal actor, CreateCourseRequest req) {
        Long tenantId = TenantContext.require();
        Course c = new Course();
        c.setTenantId(tenantId);
        c.setBranchId(req.branchId() != null ? req.branchId() : actor.getBranchId());
        c.setTitle(req.title());
        c.setSubject(req.subject());
        c.setGradeLevel(req.gradeLevel());
        c.setDescription(req.description());
        c.setPrice(req.price() != null ? req.price() : BigDecimal.ZERO);
        // A teacher's own courses are theirs; an assistant inside a teacher's academy creates courses for that teacher.
        Long acting = teacherScope.teacherIdFor(actor);
        c.setTeacherId(acting != null ? acting : req.teacherId());
        if (c.getTeacherId() == null) {
            var teachers = users.findByTenantIdAndRole(tenantId, com.manarah.identity.domain.Role.TEACHER);
            if (teachers.size() == 1) c.setTeacherId(teachers.getFirst().getId());
        }
        if (c.getTeacherId() != null) users.findByTenantIdAndId(tenantId, c.getTeacherId())
                .filter(u -> u.getRole() == com.manarah.identity.domain.Role.TEACHER)
                .orElseThrow(() -> new com.manarah.common.exception.ApiExceptions.BadRequestException("اختر مدرساً من هذه المساحة"));
        String cover = req.coverUrl() == null ? "" : req.coverUrl().trim();
        if (!cover.isEmpty() && !cover.matches("^https://[^\\s]+$") && !cover.matches("^/api/public/images/[0-9]+$")
                && !cover.matches("^/images/[a-zA-Z0-9._-]+$"))
            throw new com.manarah.common.exception.ApiExceptions.BadRequestException("رابط صورة الكورس غير صحيح");
        c.setCoverUrl(cover.isEmpty() ? null : cover);
        c.setSchedule(req.schedule());
        c.setGrade(req.grade());
        courses.save(c);
        if ("ACTIVE".equals(c.getStatus())) events.publishEvent(new com.manarah.common.events.DomainEvents.CourseOffered(tenantId, c.getId()));
        return get(c.getId(), true);
    }

    @Transactional
    public ModuleView addModule(Long courseId, CreateModuleRequest req) {
        Long tenantId = TenantContext.require();
        courses.findByTenantIdAndId(tenantId, courseId).orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        CourseModule m = new CourseModule();
        m.setTenantId(tenantId);
        m.setCourseId(courseId);
        m.setTitle(req.title());
        m.setPosition(req.position() != null ? req.position()
                : modules.findByTenantIdAndCourseIdOrderByPosition(tenantId, courseId).size() + 1);
        modules.save(m);
        return new ModuleView(m.getId(), m.getTitle(), m.getPosition(), List.of(), m.getCreatedAt());
    }

    @Transactional
    public LessonView addLesson(Long moduleId, CreateLessonRequest req) {
        Long tenantId = TenantContext.require();
        Lesson l = new Lesson();
        l.setTenantId(tenantId);
        l.setModuleId(moduleId);
        l.setTitle(req.title());
        l.setPosition(req.position() != null ? req.position()
                : lessons.findByTenantIdAndModuleIdOrderByPosition(tenantId, moduleId).size() + 1);
        l.setDurationMin(req.durationMin() != null ? req.durationMin() : 0);
        l.setContentText(req.contentText());
        l.setReleaseAt(req.releaseAt());
        lessons.save(l);
        // A scheduled lesson isn't news yet: students only hear about lessons they can open now.
        if (l.getReleaseAt() == null || !l.getReleaseAt().isAfter(Instant.now()))
            modules.findById(moduleId).ifPresent(m -> events.publishEvent(
                    new com.manarah.common.events.DomainEvents.LessonAdded(tenantId, m.getCourseId(), l.getId())));
        return new LessonView(l.getId(), l.getTitle(), l.getPosition(), l.getDurationMin(), l.getContentText(),
                List.of(), l.getCreatedAt(), l.getReleaseAt(), l.getAiSummary());
    }

    @Transactional
    public ModuleView renameModule(Long moduleId, UpdateModuleRequest req) {
        Long tenantId = TenantContext.require();
        CourseModule m = modules.findById(moduleId).filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> NotFoundException.of("الفصل", moduleId));
        m.setTitle(req.title().trim());
        modules.save(m);
        return new ModuleView(m.getId(), m.getTitle(), m.getPosition(), List.of(), m.getCreatedAt());
    }

    /** Removes the chapter with all its lessons, their files, and every student's progress in them. */
    @Transactional
    public void deleteModule(Long moduleId) {
        Long tenantId = TenantContext.require();
        CourseModule m = modules.findById(moduleId).filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> NotFoundException.of("الفصل", moduleId));
        for (Lesson l : lessons.findByTenantIdAndModuleIdOrderByPosition(tenantId, moduleId)) removeLessonData(tenantId, l);
        modules.delete(m);
    }

    @Transactional
    public LessonView updateLesson(Long lessonId, UpdateLessonRequest req) {
        Long tenantId = TenantContext.require();
        Lesson l = lessons.findById(lessonId).filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> NotFoundException.of("الدرس", lessonId));
        l.setTitle(req.title().trim());
        if (req.durationMin() != null) l.setDurationMin(Math.max(0, req.durationMin()));
        l.setContentText(req.contentText());
        l.setReleaseAt(req.releaseAt());
        lessons.save(l);
        List<MaterialView> mats = materials.findByTenantIdAndLessonId(tenantId, l.getId()).stream().map(this::toMaterial).toList();
        return new LessonView(l.getId(), l.getTitle(), l.getPosition(), l.getDurationMin(), l.getContentText(), mats,
                l.getCreatedAt(), l.getReleaseAt(), l.getAiSummary());
    }

    /** Removes the lesson together with its materials, checkpoints and every student's progress in it. */
    @Transactional
    public void deleteLesson(Long lessonId) {
        Long tenantId = TenantContext.require();
        Lesson l = lessons.findById(lessonId).filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> NotFoundException.of("الدرس", lessonId));
        removeLessonData(tenantId, l);
    }

    // Children first: watch sessions point at materials, checkpoint answers at checkpoints.
    private void removeLessonData(Long tenantId, Lesson l) {
        Long id = l.getId();
        watchSessions.deleteByTenantIdAndLessonId(tenantId, id);
        progress.deleteByTenantIdAndLessonId(tenantId, id);
        var checkpointIds = checkpoints.findByTenantIdAndLessonIdOrderByPositionAsc(tenantId, id).stream().map(LessonCheckpoint::getId).toList();
        if (!checkpointIds.isEmpty()) checkpointAnswers.deleteByTenantIdAndCheckpointIdIn(tenantId, checkpointIds);
        checkpoints.deleteByTenantIdAndLessonId(tenantId, id);
        materials.deleteByTenantIdAndLessonId(tenantId, id);
        lessons.delete(l);
    }

    @Transactional
    public MaterialView addMaterial(Long lessonId, CreateMaterialRequest req) {
        Long tenantId = TenantContext.require();
        if (!java.util.Set.of("VIDEO", "PDF", "PPT", "DOC", "IMAGE", "AUDIO", "LINK").contains(req.type()))
            throw new com.manarah.common.exception.ApiExceptions.BadRequestException("نوع الملف غير مدعوم");
        if ((req.url() == null || req.url().isBlank()) && (req.fileKey() == null || req.fileKey().isBlank()))
            throw new com.manarah.common.exception.ApiExceptions.BadRequestException("أضف ملفاً أو رابطاً للمادة");
        if (req.url() != null && !req.url().isBlank() && !req.url().matches("(?i)^https?://[^\\s]+$"))
            throw new com.manarah.common.exception.ApiExceptions.BadRequestException("استخدم رابط http أو https صالحاً");
        if (req.fileKey() != null && (!req.fileKey().startsWith("t" + tenantId + "/materials/") || req.fileKey().contains("..")))
            throw new com.manarah.common.exception.ApiExceptions.BadRequestException("ملف غير صالح");
        LessonMaterial mat = new LessonMaterial();
        mat.setTenantId(tenantId);
        mat.setLessonId(lessonId);
        mat.setType(req.type());
        mat.setTitle(req.title());
        mat.setDescription(req.description());
        mat.setUrl(req.url());
        mat.setFileKey(req.fileKey());
        mat.setSizeBytes(req.sizeBytes());
        mat.setDurationSec(req.durationSec());
        materials.save(mat);
        return toMaterial(mat);
    }

    @Transactional
    public CourseSummary setDiscount(Long courseId, CourseDtos.SetDiscountRequest req) {
        Long tenantId = TenantContext.require();
        Course c = courses.findByTenantIdAndId(tenantId, courseId).orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        Integer pct = req.discountPercent();
        if (pct != null && (pct < 0 || pct > 100))
            throw new com.manarah.common.exception.ApiExceptions.BadRequestException("نسبة الخصم يجب أن تكون بين 0 و100");
        c.setDiscountPercent(pct == null || pct == 0 ? null : pct);
        courses.save(c);
        return toSummary(tenantId, c);
    }

    private CourseSummary toSummary(Long tenantId, Course c) {
        String teacherName = c.getTeacherId() == null ? null
                : users.findById(c.getTeacherId()).map(u -> u.getFullName()).orElse(null);
        long count = enrollments.countStudying(tenantId, c.getId());
        return new CourseSummary(c.getId(), c.getTitle(), c.getSubject(), c.getGradeLevel(), c.getStatus(),
                c.getPrice(), c.getTeacherId(), teacherName, count, c.getCoverUrl(), c.getSchedule(), c.getGrade(),
                c.getDiscountPercent(), c.getFinalPrice(), academies.findByTenantId(tenantId).map(a -> a.getId()).orElse(null));
    }

    private MaterialView toMaterial(LessonMaterial m) {
        return new MaterialView(m.getId(), m.getType(), m.getTitle(), m.getDescription(), m.getUrl(), m.getFileKey(),
                m.getSizeBytes(), m.getDurationSec(), m.getCreatedAt());
    }
}
