package com.manarah.admin;

import com.manarah.academy.*;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.CourseDtos.CreateCourseRequest;
import com.manarah.course.CourseDtos.EditCourseRequest;
import com.manarah.course.CourseService;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.repo.TenantRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Head office's one page per teacher: the teacher's page, their sign-in, whether they show on the site, and every
 * course they teach — made in one go, edited in one place, and deleted from there too.
 *
 * <p>Deleting archives rather than drops. Payments, attendance, grades, certificates and the audit log all point at
 * the teacher's space and courses, so the rows stay; what the owner sees is the teacher gone from the site, the admin
 * screens and their students' lists, their sign-in closed, and their page link and username free to use again.
 */
@Service
public class AdminTeacherService {
    private static final String ARCHIVED = "ARCHIVED";
    private static final String FREED_PREFIX = "deleted-";

    private final BundleService bundleService;
    private final AcademyService academyService;
    private final CourseService courseService;
    private final TeacherAcademyRepository academies;
    private final TeacherBundleMemberRepository bundleMembers;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final com.manarah.student.repo.StudentRepository students;
    private final com.manarah.audit.AuditService audit;

    public AdminTeacherService(BundleService bundleService, AcademyService academyService, CourseService courseService,
                               TeacherAcademyRepository academies, TeacherBundleMemberRepository bundleMembers, TenantRepository tenants,
                               UserRepository users, CourseRepository courses, EnrollmentRepository enrollments,
                               com.manarah.student.repo.StudentRepository students, com.manarah.audit.AuditService audit) {
        this.bundleService = bundleService; this.academyService = academyService; this.courseService = courseService;
        this.academies = academies; this.bundleMembers = bundleMembers; this.tenants = tenants; this.users = users;
        this.courses = courses; this.enrollments = enrollments; this.students = students;
        this.audit = audit;
    }

    /** The teacher page. On create, {@code slug}, {@code username} and {@code password} are required; on an edit
     *  the link stays, a blank password keeps the current one, and {@code courses} is ignored (courses have their own calls). */
    public record TeacherForm(String name, String slug, String subject, String tagline, String headline, String description,
                              String aboutText, String phone, Boolean published, String username, String password, String email,
                              List<EditCourseRequest> courses) {}

    public record CourseView(Long id, String title, String subject, String gradeLevel, String grade, String description,
                             BigDecimal price, Integer discountPercent, BigDecimal finalPrice, String coverUrl, String status,
                             long students) {}

    public record TeacherDetail(Long academyId, String slug, String name, String subject, String tagline, String headline,
                                String description, String aboutText, String phone, String photoUrl, String coverUrl,
                                boolean published, String username, String email, long students, List<CourseView> courses) {}

    // ---- Teacher ----------------------------------------------------------------------------------------------

    public TeacherDetail detail(UserPrincipal actor, Long academyId) {
        return view(managed(actor, academyId));
    }

    /** Teacher, sign-in, page and courses in one transaction: if any part is refused, nothing is left half-made. */
    @Transactional
    public TeacherDetail create(UserPrincipal actor, TeacherForm req) {
        bundleService.requireHeadOffice(actor);
        String name = required(req.name(), 120, "اكتب اسم المدرس");
        String subject = required(req.subject(), 100, "اكتب المادة");
        var a = academyService.create(actor, new AcademyService.CreateAcademy(name, req.slug(), req.username(), req.password()));
        academyService.save(actor, a.getId(), content(a, req, name, subject));
        if (!blank(req.email())) academyService.teacherCredentials(actor, a.getId(),
                new AcademyService.Credentials(req.username(), req.password(), req.email()));
        List<EditCourseRequest> list = req.courses() == null ? List.of() : req.courses();
        if (list.size() > 60) throw new BadRequestException("أضف ٦٠ كورس بحد أقصى مرة واحدة");
        for (EditCourseRequest c : list) addCourse(a, c, subject);
        audit.record(actor, "TEACHER_CREATED_WITH_COURSES", "TeacherAcademy", a.getId(), null, a.getSlug() + " courses=" + list.size());
        return view(academies.findById(a.getId()).orElseThrow());
    }

    @Transactional
    public TeacherDetail update(UserPrincipal actor, Long academyId, TeacherForm req) {
        var a = managed(actor, academyId);
        String name = required(req.name(), 120, "اكتب اسم المدرس");
        String subject = required(req.subject(), 100, "اكتب المادة");
        academyService.save(actor, a.getId(), content(a, req, name, subject));
        User teacher = users.findById(a.getTeacherId()).orElseThrow();
        String email = blank(req.email()) ? "" : req.email().trim();
        boolean credentialsChanged = !blank(req.password())
                || (!blank(req.username()) && !req.username().trim().equalsIgnoreCase(Objects.toString(teacher.getUsername(), "")))
                || (!email.isEmpty() && !email.equalsIgnoreCase(teacher.getEmail()));
        if (credentialsChanged) academyService.teacherCredentials(actor, a.getId(), new AcademyService.Credentials(
                blank(req.username()) ? teacher.getUsername() : req.username(), req.password(), email.isEmpty() ? null : email));
        audit.record(actor, "TEACHER_UPDATED_BY_ADMIN", "TeacherAcademy", a.getId(), null, a.getSlug());
        return view(academies.findById(a.getId()).orElseThrow());
    }

    /** See the class comment: the teacher disappears everywhere, their history stays in the database. */
    @Transactional
    public void delete(UserPrincipal actor, Long academyId) {
        var a = managed(actor, academyId);
        String slug = a.getSlug(), freed = FREED_PREFIX + a.getId() + "-" + slug;
        a.setArchivedAt(Instant.now());
        a.setPublished(false);
        a.setDefaultHome(false);
        a.setSlug(freed);
        academies.save(a);
        tenants.findById(a.getTenantId()).ifPresent(t -> { t.setSlug(freed); tenants.save(t); });
        // The teacher and their assistants can no longer sign in; their username and email are free again.
        for (Role role : List.of(Role.TEACHER, Role.ASSISTANT))
            for (User u : users.findByTenantIdAndRole(a.getTenantId(), role)) {
                u.setStatus(ARCHIVED);
                if (u.getUsername() != null && !u.getUsername().startsWith(FREED_PREFIX)) u.setUsername(FREED_PREFIX + u.getId() + "-" + u.getUsername());
                if (u.getEmail() != null && !u.getEmail().startsWith(FREED_PREFIX)) u.setEmail(FREED_PREFIX + u.getId() + "-" + u.getEmail());
                users.save(u);
            }
        for (Course c : courses.findByTenantId(a.getTenantId())) courseService.retire(actor, c);
        bundleMembers.deleteAll(bundleMembers.findByAcademyId(a.getId()));
        audit.record(actor, "ACADEMY_DELETED", "TeacherAcademy", a.getId(), slug, null);
    }

    // ---- Courses ----------------------------------------------------------------------------------------------

    @Transactional
    public CourseView addCourse(UserPrincipal actor, Long academyId, EditCourseRequest req) {
        var a = managed(actor, academyId);
        Course c = addCourse(a, req, a.getSubject());
        audit.record(actor, "COURSE_CREATED_BY_ADMIN", "Course", c.getId(), null, c.getTitle());
        return view(c);
    }

    /** Edits any of the course's details; fields left null stay as they are (see {@link CourseService#edit}). */
    @Transactional
    public CourseView updateCourse(UserPrincipal actor, Long courseId, EditCourseRequest req) {
        return view(courseService.edit(actor, managedCourse(actor, courseId), req));
    }

    /** The course is gone from every list and closed to its students; its payments and history stay. */
    @Transactional
    public void deleteCourse(UserPrincipal actor, Long courseId) {
        courseService.retire(actor, managedCourse(actor, courseId));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------

    private Course addCourse(TeacherAcademy a, EditCourseRequest req, String teacherSubject) {
        Long branchId = users.findById(a.getTeacherId()).map(User::getBranchId).orElseThrow();
        String subject = blank(req.subject()) ? teacherSubject : optional(req.subject(), 100);
        var create = new CreateCourseRequest(required(req.title(), 200, "اكتب اسم كل كورس"), subject, optional(req.gradeLevel(), 60),
                optional(req.description(), 5000), CourseService.validPrice(req.price() == null ? BigDecimal.ZERO : req.price()), a.getTeacherId(),
                branchId, req.coverUrl(), null, optional(req.grade(), 80));
        Course c = courseService.createIn(a.getTenantId(), branchId, a.getTeacherId(), create,
                req.status() == null ? "ACTIVE" : CourseService.visibleStatus(req.status()));
        if (req.discountPercent() != null && req.discountPercent() > 0) { c.setDiscountPercent(CourseService.validDiscount(req.discountPercent())); courses.save(c); }
        return c;
    }

    /** What the page says is the teacher's; blanks get a sensible line built from the name and subject. The payment
     *  numbers, demo flag, videos and posters are not on this page, so they stay exactly as they were. */
    private AcademyService.Content content(TeacherAcademy a, TeacherForm req, String name, String subject) {
        String description = blank(req.description())
                ? "من أول فكرة لحد أصعب مسألة، هنفهم ونطبّق ونراجع سوا. رحلتك في " + subject + " تبدأ هنا، خطوة بخطوة مع " + name + "."
                : req.description();
        return new AcademyService.Content(name,
                blank(req.tagline()) ? "مدرس " + subject : req.tagline(),
                blank(req.headline()) ? subject + " بشرح بسيط وخطوات واضحة" : req.headline(),
                description,
                blank(req.aboutText()) ? description : req.aboutText(),
                subject, req.phone(), a.isDemoContent(), req.published() == null ? a.isPublished() : req.published(), null,
                Objects.toString(a.getInstapayNumber(), ""), Objects.toString(a.getVodafoneCashNumber(), ""),
                Objects.toString(a.getPaymentNote(), ""), null, null, null);
    }

    private TeacherAcademy managed(UserPrincipal actor, Long academyId) {
        bundleService.requireHeadOffice(actor);
        return academies.findById(academyId)
                .filter(a -> !a.isArchived() && actor.getTenantId().equals(a.getManagerTenantId()))
                .orElseThrow(() -> NotFoundException.of("المدرس", academyId));
    }

    private Course managedCourse(UserPrincipal actor, Long courseId) {
        bundleService.requireHeadOffice(actor);
        Course c = courses.findById(courseId).filter(x -> !Course.DELETED.equals(x.getStatus()))
                .orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        academies.findByTenantId(c.getTenantId())
                .filter(a -> !a.isArchived() && actor.getTenantId().equals(a.getManagerTenantId()))
                .orElseThrow(() -> new ForbiddenException("الكورس ده تابع لإدارة تانية"));
        return c;
    }

    private TeacherDetail view(TeacherAcademy a) {
        User teacher = users.findById(a.getTeacherId()).orElseThrow();
        String email = teacher.getEmail() != null && !teacher.getEmail().endsWith("@accounts.local") ? teacher.getEmail() : "";
        List<CourseView> list = courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId()).stream()
                .sorted(Comparator.comparing(Course::getId)).map(this::view).toList();
        // The public image links only answer for a published page, so the editor previews the stored images directly.
        return new TeacherDetail(a.getId(), a.getSlug(), a.getName(), Objects.toString(a.getSubject(), ""),
                Objects.toString(a.getTagline(), ""), Objects.toString(a.getHeadline(), ""), Objects.toString(a.getDescription(), ""),
                Objects.toString(a.getAboutText(), ""), Objects.toString(a.getPhone(), ""),
                a.getPhotoData() != null ? a.getPhotoData() : Objects.toString(a.getPhotoUrl(), ""),
                Objects.toString(a.getCoverData(), ""),
                a.isPublished(), Objects.toString(teacher.getUsername(), ""), email,
                students.countByTenantIdAndStatusNot(a.getTenantId(), ARCHIVED), list);
    }

    private CourseView view(Course c) {
        return new CourseView(c.getId(), c.getTitle(), Objects.toString(c.getSubject(), ""), Objects.toString(c.getGradeLevel(), ""),
                Objects.toString(c.getGrade(), ""), Objects.toString(c.getDescription(), ""), c.getPrice(), c.getDiscountPercent(),
                c.getFinalPrice(), Objects.toString(c.getCoverUrl(), ""), c.getStatus(), enrollments.countStudying(c.getTenantId(), c.getId()));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String required(String s, int max, String message) {
        if (blank(s)) throw new BadRequestException(message);
        if (s.trim().length() > max) throw new BadRequestException("النص أطول من المسموح (" + max + " حرف)");
        return s.trim();
    }

    private static String optional(String s, int max) {
        if (blank(s)) return null;
        if (s.trim().length() > max) throw new BadRequestException("النص أطول من المسموح (" + max + " حرف)");
        return s.trim();
    }
}
