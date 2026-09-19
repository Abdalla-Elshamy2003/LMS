package com.manarah.academy;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.identity.domain.*;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.domain.*;
import com.manarah.org.repo.*;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AcademyService {
    private final TeacherAcademyRepository academies;
    private final TenantRepository tenants;
    private final BranchRepository branches;
    private final UserRepository users;
    private final StudentRepository students;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final PasswordEncoder passwords;
    private final com.manarah.audit.AuditService audit;
    public AcademyService(TeacherAcademyRepository academies, TenantRepository tenants, BranchRepository branches,
            UserRepository users, StudentRepository students, CourseRepository courses, EnrollmentRepository enrollments, PasswordEncoder passwords, com.manarah.audit.AuditService audit) {
        this.academies = academies; this.tenants = tenants; this.branches = branches; this.users = users;
        this.students = students; this.courses = courses; this.enrollments = enrollments; this.passwords = passwords;
        this.audit = audit;
    }
    /** Status for a student removed from an academy — see {@link #removeStudent}. */
    private static final String ARCHIVED = "ARCHIVED";
    /** Marks an archived login so its username can be reused. */
    private static final String FREED_PREFIX = "deleted-";

    public record CreateAcademy(String name, String slug, String username, String password, Long ownerUserId) {
        public CreateAcademy(String name, String slug, String username, String password) {
            this(name, slug, username, password, null);
        }
    }
    public record Content(String name, String tagline, String headline, String description, String aboutText,
                          String subject, String phone, boolean demoContent, boolean published, List<Video> videos,
                          String instapayNumber, String vodafoneCashNumber, String paymentNote) {
        public Content(String name, String tagline, String headline, String description, String aboutText,
                       String subject, String phone, boolean demoContent, boolean published, List<Video> videos) {
            this(name, tagline, headline, description, aboutText, subject, phone, demoContent, published, videos, "", "", "");
        }
    }
    public record Video(String title, String description, String url, String poster, String category) {}
    public record Account(String fullName, String username, String password, List<Long> courseIds) {}
    public record Credentials(String username, String password) {}

    public List<TeacherAcademy> list(UserPrincipal actor) {
        var own = academies.findByTenantId(actor.getTenantId());
        if (own.isPresent()) return List.of(own.get());
        if (!actor.isAdmin()) {
            // A teacher with a school account sees the page(s) linked to that account. Teachers who
            // have no page yet get an empty list, not an error — the screen explains it instead.
            return academies.findByOwnerUserId(actor.getId());
        }
        return academies.findByManagerTenantId(actor.getTenantId());
    }
    public TeacherAcademy manage(UserPrincipal actor, Long id) {
        var a = academies.findById(id).orElseThrow(() -> NotFoundException.of("صفحة المدرس", id));
        if ((actor.isAdmin() && (a.getManagerTenantId().equals(actor.getTenantId()) || a.getTenantId().equals(actor.getTenantId())))
                || (actor.getRole() == Role.TEACHER && a.getTeacherId().equals(actor.getId()) && a.getTenantId().equals(actor.getTenantId()))
                || Objects.equals(a.getOwnerUserId(), actor.getId())) return a;
        throw new ForbiddenException("لا يمكنك إدارة مساحة مدرس آخر");
    }

    /** Links an existing staff account to a teacher page so they manage it with their normal login. */
    @Transactional
    public TeacherAcademy assignOwner(UserPrincipal actor, Long id, Long userId) {
        if (!actor.isAdmin()) throw new ForbiddenException("ربط الحساب متاح للإدارة فقط");
        var a = academies.findById(id).orElseThrow(() -> NotFoundException.of("صفحة المدرس", id));
        if (!a.getManagerTenantId().equals(actor.getTenantId())) throw new ForbiddenException("هذه الصفحة تتبع إدارة أخرى");
        if (userId == null) { a.setOwnerUserId(null); return academies.save(a); }
        var u = users.findById(userId).filter(x -> x.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("المستخدم", userId));
        if (u.getRole() != Role.TEACHER) throw new BadRequestException("اختر حساب مدرس");
        a.setOwnerUserId(u.getId());
        audit.record(actor, "ACADEMY_OWNER_LINKED", "TeacherAcademy", a.getId(), null, "user=" + u.getId());
        return academies.save(a);
    }
    private String required(String s, int max) {
        if (s == null || s.isBlank() || s.length() > max) throw new BadRequestException("أكمل الحقول المطلوبة ضمن الحد المسموح");
        return s.trim();
    }
    private void credentials(User u, String username, String password) {
        String name = required(username, 50).toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9._-]{2,49}")) throw new BadRequestException("اسم المستخدم من 3 إلى 50 حرفاً إنجليزياً أو رقماً أو . أو _ أو -");
        var existing = users.findByUsernameIgnoreCase(name);
        if (existing.isPresent() && !Objects.equals(existing.get().getId(), u.getId())) throw new ConflictException("اسم المستخدم مستخدم بالفعل");
        u.setUsername(name); u.setPasswordHash(passwords.encode(PasswordPolicy.requireStrong(password))); u.setStatus("ACTIVE");
    }
    @Transactional
    public TeacherAcademy create(UserPrincipal actor, CreateAcademy req) {
        if (!actor.isAdmin() || academies.findByTenantId(actor.getTenantId()).isPresent()) throw new ForbiddenException("إنشاء مساحة مدرس متاح من الإدارة الرئيسية فقط");
        String slug = required(req.slug(), 70).toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new BadRequestException("استخدم حروفاً إنجليزية وأرقاماً وشرطات في الرابط");
        if (tenants.existsBySlug(slug)) throw new ConflictException("رابط الصفحة مستخدم بالفعل");
        var t = new Tenant(); t.setName(required(req.name(), 120)); t.setSlug(slug); tenants.save(t);
        var b = new Branch(); b.setTenantId(t.getId()); b.setName(t.getName()); branches.save(b);
        var u = new User(); u.setTenantId(t.getId()); u.setBranchId(b.getId()); u.setFullName(t.getName());
        u.setEmail(UUID.randomUUID() + "@accounts.local"); u.setRole(Role.TEACHER); credentials(u, req.username(), req.password()); users.save(u);
        var a = new TeacherAcademy(); a.setTenantId(t.getId()); a.setManagerTenantId(actor.getTenantId());
        a.setTeacherId(u.getId()); a.setName(t.getName()); a.setSlug(slug); a.setDefaultHome(academies.count() == 0);
        if (req.ownerUserId() != null) users.findById(req.ownerUserId())
                .filter(o -> o.getTenantId().equals(actor.getTenantId()) && o.getRole() == Role.TEACHER)
                .ifPresent(o -> a.setOwnerUserId(o.getId()));
        a.setDemoContent(false);
        a.setDescription("من أول فكرة لحد أصعب مسألة، هنفهم ونطبّق ونراجع سوا. رحلتك تبدأ هنا، خطوة بخطوة مع مستر " + a.getName() + ".");
        academies.save(a);
        audit.record(actor, "ACADEMY_CREATED", "TeacherAcademy", a.getId(), null, a.getSlug());
        return a;
    }
    @Transactional
    public TeacherAcademy save(UserPrincipal actor, Long id, Content req) {
        var a = manage(actor, id); a.setName(required(req.name(), 120)); a.setTagline(required(req.tagline(), 150));
        a.setHeadline(required(req.headline(), 220)); a.setDescription(required(req.description(), 1500));
        a.setAboutText(required(req.aboutText(), 3000)); a.setSubject(required(req.subject(), 100));
        String phone = req.phone() == null ? "" : req.phone().trim();
        if (!phone.isEmpty() && !phone.matches("[+0-9 ()-]{7,25}")) throw new BadRequestException("رقم التواصل غير صحيح");
        a.setPhone(phone); a.setDemoContent(req.demoContent()); a.setPublished(req.published());
        String instapay = req.instapayNumber() == null ? "" : req.instapayNumber().trim();
        String vodafone = req.vodafoneCashNumber() == null ? "" : req.vodafoneCashNumber().trim();
        if (instapay.length() > 40) throw new BadRequestException("رقم إنستاباي طويل جداً");
        if (vodafone.length() > 40) throw new BadRequestException("رقم فودافون كاش طويل جداً");
        String paymentNote = req.paymentNote() == null ? "" : req.paymentNote().trim();
        if (paymentNote.length() > 500) throw new BadRequestException("ملاحظة الدفع طويلة جداً");
        a.setInstapayNumber(instapay); a.setVodafoneCashNumber(vodafone); a.setPaymentNote(paymentNote);
        if (req.videos() != null) {
            if (req.videos().size() > 30) throw new BadRequestException("يمكن إضافة 30 فيديو بحد أقصى");
            var videos = req.videos().stream().map(v -> {
                String title = required(v.title(), 150), url = required(v.url(), 2000);
                if (!url.matches("^https://[^\\s]+$") && !url.matches("^/videos/[a-zA-Z0-9._-]+\\.mp4$")) throw new BadRequestException("استخدم رابط HTTPS مباشر لفيديو MP4");
                String poster = v.poster() == null ? "" : v.poster().trim();
                if (!poster.isEmpty() && !poster.matches("^https://[^\\s]+$") && !poster.matches("^/videos/[a-zA-Z0-9._-]+\\.png$")) throw new BadRequestException("رابط غلاف الفيديو غير صحيح");
                String description = v.description() == null ? "" : v.description().trim();
                if (description.length() > 500) throw new BadRequestException("وصف الفيديو طويل جداً");
                return new Video(title, description, url, poster, required(v.category(), 80));
            }).toList();
            try { a.setVideosJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(videos)); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        }
        var t = tenants.findById(a.getTenantId()).orElseThrow(); t.setName(a.getName());
        var u = users.findById(a.getTeacherId()).orElseThrow(); u.setFullName(a.getName()); u.setSubjects(a.getSubject()); u.setBio(a.getAboutText()); u.setTitle(a.getTagline());
        u.setPhotoUrl(a.getPhotoUrl()); return academies.save(a);
    }
    @Transactional
    public void home(UserPrincipal actor, Long id) {
        var a = manage(actor, id);
        if (!actor.isAdmin() || !a.getManagerTenantId().equals(actor.getTenantId())) throw new ForbiddenException("متاح للإدارة الرئيسية فقط");
        var current = academies.findByDefaultHomeTrue();
        if (current.isPresent() && !current.get().getManagerTenantId().equals(actor.getTenantId())) throw new ForbiddenException("الصفحة الرئيسية تابعة لإدارة أخرى");
        current.ifPresent(old -> { old.setDefaultHome(false); academies.saveAndFlush(old); });
        a.setDefaultHome(true); academies.save(a);
        audit.record(actor, "ACADEMY_HOME_CHANGED", "TeacherAcademy", id, null, a.getSlug());
    }
    @Transactional
    public void teacherCredentials(UserPrincipal actor, Long id, Credentials req) {
        var a = manage(actor, id);
        if (!actor.isAdmin()) throw new ForbiddenException("تعديل حساب المدرس متاح للإدارة فقط");
        var u = users.findById(a.getTeacherId()).orElseThrow(); credentials(u, req.username(), req.password()); users.save(u);
        audit.record(actor, "TEACHER_CREDENTIALS_CHANGED", "TeacherAcademy", id, null, "credentials rotated");
    }
    public List<Map<String,Object>> accounts(UserPrincipal actor, Long id) {
        var a = manage(actor, id);
        return students.findByTenantId(a.getTenantId()).stream()
            .filter(s -> s.getUserId() != null && !ARCHIVED.equals(s.getStatus())).map(s -> {
            var u = users.findById(s.getUserId()).orElseThrow();
            return Map.<String,Object>of("id", s.getId(), "fullName", s.getFullName(), "username", Objects.toString(u.getUsername(), u.getEmail()),
                "courseIds", enrollments.findByTenantIdAndStudentId(a.getTenantId(), s.getId()).stream()
                .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).map(Enrollment::getCourseId).toList());
        }).toList();
    }
    private Set<Long> validCourses(TeacherAcademy a, List<Long> ids) {
        var selected = new HashSet<>(ids == null ? List.<Long>of() : ids);
        for (Long id : selected) courses.findByTenantIdAndId(a.getTenantId(), id)
            .filter(c -> Objects.equals(c.getTeacherId(), a.getTeacherId()))
            .orElseThrow(() -> new BadRequestException("اختر كورسات هذا المدرس فقط"));
        return selected;
    }
    @Transactional
    public Map<String,Object> createStudent(UserPrincipal actor, Long id, Account req) {
        var a = manage(actor, id); var ids = validCourses(a, req.courseIds());
        var teacher = users.findById(a.getTeacherId()).orElseThrow();
        var u = new User(); u.setTenantId(a.getTenantId()); u.setBranchId(teacher.getBranchId()); u.setRole(Role.STUDENT);
        u.setFullName(required(req.fullName(), 120)); u.setEmail(UUID.randomUUID() + "@accounts.local"); credentials(u, req.username(), req.password()); users.save(u);
        var s = new Student(); s.setTenantId(a.getTenantId()); s.setBranchId(teacher.getBranchId()); s.setFullName(u.getFullName());
        s.setUserId(u.getId()); s.setCode("STD-" + UUID.randomUUID()); students.save(s);
        replaceAccess(a, s.getId(), ids);
        audit.record(actor, "ACADEMY_STUDENT_CREATED", "Student", s.getId(), null, "academy=" + id);
        return Map.of("id", s.getId(), "username", u.getUsername());
    }
    private void replaceAccess(TeacherAcademy a, Long studentId, Set<Long> ids) {
        var current = enrollments.findByTenantIdAndStudentId(a.getTenantId(), studentId);
        for (var e : current) { e.setStatus(ids.remove(e.getCourseId()) ? "ACTIVE" : "INACTIVE"); enrollments.save(e); }
        for (Long courseId : ids) { var e = new Enrollment(); e.setTenantId(a.getTenantId()); e.setStudentId(studentId); e.setCourseId(courseId); enrollments.save(e); }
    }
    @Transactional
    public void access(UserPrincipal actor, Long id, Long studentId, Account req) {
        var a = manage(actor, id); var ids = validCourses(a, req.courseIds());
        var s = students.findByTenantIdAndId(a.getTenantId(), studentId).orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        // The name lives on both the login user and the student record, so a correction has to
        // touch both or the two views of the same person drift apart.
        if (req.fullName() != null && !req.fullName().isBlank()) {
            var u = users.findByTenantIdAndId(a.getTenantId(), s.getUserId()).orElseThrow();
            u.setFullName(required(req.fullName(), 120)); users.save(u);
            s.setFullName(u.getFullName()); students.save(s);
        }
        if (req.password() != null && !req.password().isBlank()) {
            var u = users.findByTenantIdAndId(a.getTenantId(), s.getUserId()).orElseThrow();
            credentials(u, u.getUsername(), req.password()); users.save(u);
        }
        replaceAccess(a, studentId, ids);
        audit.record(actor, "ACADEMY_STUDENT_ACCESS_CHANGED", "Student", studentId, null, "academy=" + id);
    }

    /**
     * Removes a student from the academy. Archives rather than hard-deletes: attendance records,
     * grades, submissions and gate logs all point at this student row, and foreign keys are
     * enforced, so a real DELETE would either fail or take the student's whole
     * history with it. Archiving gets the same visible result — gone from the academy's list, and
     * login refused, since AuthService only admits status ACTIVE — while the history survives.
     *
     * <p>The username is prefixed on the way out so the teacher can immediately re-create the same
     * student under the same login name; credentials() rejects duplicates and would otherwise be
     * blocked forever by an account nobody can see any more.
     */
    @Transactional
    public void removeStudent(UserPrincipal actor, Long id, Long studentId) {
        var a = manage(actor, id);
        var s = students.findByTenantIdAndId(a.getTenantId(), studentId)
            .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        replaceAccess(a, studentId, new HashSet<>());
        s.setStatus(ARCHIVED);
        students.save(s);
        audit.record(actor, "ACADEMY_STUDENT_ARCHIVED", "Student", studentId, null, "academy=" + id);
        if (s.getUserId() == null) return;
        users.findByTenantIdAndId(a.getTenantId(), s.getUserId()).ifPresent(u -> {
            u.setStatus(ARCHIVED);
            if (u.getUsername() != null && !u.getUsername().startsWith(FREED_PREFIX))
                u.setUsername(FREED_PREFIX + u.getId() + "-" + u.getUsername());
            users.save(u);
        });
    }
}
