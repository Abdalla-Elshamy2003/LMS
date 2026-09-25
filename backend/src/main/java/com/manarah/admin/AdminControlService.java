package com.manarah.admin;

import com.manarah.academy.*;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.PasswordPolicy;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Head office's control center: the whole platform in one place — every teacher space it manages, every student
 * (as a person, across all of their teachers), every course, and the packages. Only head-office admins get here,
 * and every change is limited to the teacher spaces this head office manages and written to the audit log.
 */
@Service
public class AdminControlService {
    private final BundleService bundleService;
    private final TeacherAcademyRepository academies;
    private final TeacherBundleRepository bundles;
    private final TeacherBundleMemberRepository bundleMembers;
    private final BundleSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final StudentRepository students;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final PasswordEncoder passwords;
    private final com.manarah.audit.AuditService audit;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AdminControlService(BundleService bundleService, TeacherAcademyRepository academies, TeacherBundleRepository bundles,
                               TeacherBundleMemberRepository bundleMembers, BundleSubscriptionRepository subscriptions, UserRepository users,
                               StudentRepository students, CourseRepository courses, EnrollmentRepository enrollments,
                               PasswordEncoder passwords, com.manarah.audit.AuditService audit,
                               org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.bundleService = bundleService; this.academies = academies; this.bundles = bundles; this.bundleMembers = bundleMembers;
        this.subscriptions = subscriptions; this.users = users; this.students = students; this.courses = courses;
        this.enrollments = enrollments; this.passwords = passwords; this.audit = audit;
    }

    public record TeacherRow(Long academyId, String slug, String name, String subject, String photoUrl, boolean published,
                             String username, long students, long courses, int videos, List<String> packages) {}
    public record Seat(Long academyId, String teacher, String subject, Long studentId, String status) {}
    public record StudentRow(Long userId, String fullName, String login, String email, String phone, String status,
                             List<Seat> teachers, List<String> packages, java.time.Instant joinedAt) {}
    public record CourseRow(Long id, Long academyId, String teacher, String title, String year, BigDecimal price,
                            Integer discountPercent, BigDecimal finalPrice, String status, long students, String coverUrl) {}
    public record CourseUpdate(BigDecimal price, String status) {}

    // ---- Overview ---------------------------------------------------------------------------------------------

    public Map<String, Object> overview(UserPrincipal actor) {
        var managed = managed(actor);
        var tenantIds = managed.stream().map(TeacherAcademy::getTenantId).toList();
        List<Course> all = tenantIds.isEmpty() ? List.of() : courses.findByTenantIdIn(tenantIds);
        long people = tenantIds.isEmpty() ? 0 : students.countPeople(tenantIds) + students.countByTenantIdInAndUserIdIsNull(tenantIds);
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("teachers", managed.size());
        kpis.put("publishedTeachers", managed.stream().filter(TeacherAcademy::isPublished).count());
        kpis.put("students", people);
        kpis.put("courses", all.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count());
        kpis.put("packages", bundles.count());
        kpis.put("packageSubscribers", subscriptions.countByStatus("ACTIVE"));
        List<Map<String, Object>> perTeacher = managed.stream().map(a -> Map.<String, Object>of(
                "name", a.getName(), "subject", Objects.toString(a.getSubject(), ""),
                "students", students.countByTenantIdAndStatusNot(a.getTenantId(), "ARCHIVED"),
                "courses", all.stream().filter(c -> c.getTenantId().equals(a.getTenantId()) && "ACTIVE".equals(c.getStatus())).count()))
                .toList();
        Map<Long, String> teacherNames = new HashMap<>();
        managed.forEach(a -> teacherNames.put(a.getTenantId(), a.getName()));
        List<Map<String, Object>> recent = tenantIds.isEmpty() ? List.of() : students.findTop8ByTenantIdInOrderByCreatedAtDesc(tenantIds).stream()
                .map(s -> Map.<String, Object>of("name", s.getFullName(), "teacher", teacherNames.getOrDefault(s.getTenantId(), ""),
                        "at", s.getCreatedAt())).toList();
        return Map.of("kpis", kpis, "perTeacher", perTeacher, "recentStudents", recent);
    }

    // ---- Teachers ---------------------------------------------------------------------------------------------

    public List<TeacherRow> teachers(UserPrincipal actor) {
        Map<Long, List<String>> packagesOf = new HashMap<>();
        for (var b : bundles.findAll())
            for (var m : bundleMembers.findByBundleIdOrderByPositionAsc(b.getId()))
                if (m.getAcademyId() != null) packagesOf.computeIfAbsent(m.getAcademyId(), k -> new ArrayList<>()).add(b.getName());
        return managed(actor).stream().map(a -> new TeacherRow(a.getId(), a.getSlug(), a.getName(), Objects.toString(a.getSubject(), ""),
                Objects.toString(a.getPhotoUrl(), ""), a.isPublished(),
                users.findById(a.getTeacherId()).map(User::getUsername).orElse(""),
                students.countByTenantIdAndStatusNot(a.getTenantId(), "ARCHIVED"),
                courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId()).stream().filter(c -> "ACTIVE".equals(c.getStatus())).count(),
                a.getVideos().size(), packagesOf.getOrDefault(a.getId(), List.of()))).toList();
    }

    @Transactional
    public TeacherRow setPublished(UserPrincipal actor, Long academyId, boolean published) {
        var a = managedAcademy(actor, academyId);
        a.setPublished(published);
        academies.save(a);
        audit.record(actor, published ? "ACADEMY_PUBLISHED" : "ACADEMY_UNPUBLISHED", "TeacherAcademy", a.getId(), null, a.getSlug());
        return teachers(actor).stream().filter(t -> t.academyId().equals(academyId)).findFirst().orElseThrow();
    }

    // ---- Students (one row per person, across all of their teachers) ------------------------------------------

    public List<StudentRow> students(UserPrincipal actor, String q) {
        var managed = managed(actor);
        Map<Long, TeacherAcademy> byTenant = new HashMap<>();
        managed.forEach(a -> byTenant.put(a.getTenantId(), a));
        if (byTenant.isEmpty()) return List.of();
        List<Student> seats = students.findByTenantIdIn(new ArrayList<>(byTenant.keySet()));
        Map<Long, User> usersById = new HashMap<>();
        users.findAllById(seats.stream().map(Student::getUserId).filter(Objects::nonNull).distinct().toList()).forEach(u -> usersById.put(u.getId(), u));
        // group each seat under the account the student signs in with
        Map<Long, User> owners = new HashMap<>();
        Map<Long, List<Student>> byOwner = new LinkedHashMap<>();
        for (Student s : seats.stream().sorted(Comparator.comparing(Student::getCreatedAt).reversed()).toList()) {
            User u = s.getUserId() == null ? null : usersById.get(s.getUserId());
            if (u == null) continue;
            Long ownerId = u.getPrimaryUserId() == null ? u.getId() : u.getPrimaryUserId();
            owners.computeIfAbsent(ownerId, id -> id.equals(u.getId()) ? u : users.findById(id).orElse(u));
            byOwner.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(s);
        }
        Map<Long, List<String>> packagesOf = new HashMap<>();
        Map<Long, String> bundleNames = new HashMap<>();
        bundles.findAll().forEach(b -> bundleNames.put(b.getId(), b.getName()));
        for (var sub : subscriptions.findByUserIdIn(byOwner.keySet()))
            if ("ACTIVE".equals(sub.getStatus())) packagesOf.computeIfAbsent(sub.getUserId(), k -> new ArrayList<>()).add(bundleNames.getOrDefault(sub.getBundleId(), ""));
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<StudentRow> out = new ArrayList<>();
        for (var e : byOwner.entrySet()) {
            User owner = owners.get(e.getKey());
            String email = owner.getEmail() != null && !owner.getEmail().endsWith("@accounts.local") ? owner.getEmail() : "";
            String login = owner.getUsername() != null ? owner.getUsername() : email;
            if (!needle.isEmpty() && !(owner.getFullName() + " " + login + " " + email + " " + Objects.toString(owner.getPhone(), "")).toLowerCase(Locale.ROOT).contains(needle))
                continue;
            List<Seat> list = e.getValue().stream().map(s -> {
                var a = byTenant.get(s.getTenantId());
                return new Seat(a.getId(), a.getName(), Objects.toString(a.getSubject(), ""), s.getId(), s.getStatus());
            }).toList();
            out.add(new StudentRow(owner.getId(), owner.getFullName(), login, email, Objects.toString(owner.getPhone(), ""), owner.getStatus(),
                    list, packagesOf.getOrDefault(owner.getId(), List.of()), e.getValue().get(e.getValue().size() - 1).getCreatedAt()));
            if (out.size() >= 500) break;
        }
        return out;
    }

    /** Suspends or restores a student's sign-in — with every teacher at once, since it is one account. */
    @Transactional
    public void setStudentActive(UserPrincipal actor, Long userId, boolean active) {
        User owner = managedStudent(actor, userId);
        if ("ARCHIVED".equals(owner.getStatus())) throw new BadRequestException("الحساب ده محذوف من عند المدرس");
        owner.setStatus(active ? "ACTIVE" : "INACTIVE");
        users.save(owner);
        audit.record(actor, active ? "STUDENT_LOGIN_RESTORED" : "STUDENT_LOGIN_SUSPENDED", "User", owner.getId(), null, null);
    }

    /** New password for the account the student signs in with; ends their sessions with every teacher. */
    @Transactional
    public void resetStudentPassword(UserPrincipal actor, Long userId, String password) {
        User owner = managedStudent(actor, userId);
        owner.setPasswordHash(passwords.encode(PasswordPolicy.requireStrong(password)));
        users.save(owner);
        audit.record(actor, "STUDENT_PASSWORD_RESET", "User", owner.getId(), null, "credentials rotated");
    }

    // ---- Courses ----------------------------------------------------------------------------------------------

    public List<CourseRow> courses(UserPrincipal actor, String q) {
        var managed = managed(actor);
        Map<Long, TeacherAcademy> byTenant = new HashMap<>();
        managed.forEach(a -> byTenant.put(a.getTenantId(), a));
        if (byTenant.isEmpty()) return List.of();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return courses.findByTenantIdIn(new ArrayList<>(byTenant.keySet())).stream()
                .filter(c -> needle.isEmpty() || (c.getTitle() + " " + byTenant.get(c.getTenantId()).getName()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((Course c) -> byTenant.get(c.getTenantId()).getName()).thenComparing(Course::getId))
                .map(c -> row(c, byTenant.get(c.getTenantId()))).toList();
    }

    @Transactional
    public CourseRow updateCourse(UserPrincipal actor, Long courseId, CourseUpdate req) {
        var managed = managed(actor);
        Course c = courses.findById(courseId).orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        var a = managed.stream().filter(x -> x.getTenantId().equals(c.getTenantId())).findFirst()
                .orElseThrow(() -> new ForbiddenException("الكورس ده تابع لإدارة تانية"));
        String before = c.getPrice() + "/" + c.getStatus();
        if (req.price() != null) {
            if (req.price().signum() < 0 || req.price().compareTo(new BigDecimal("1000000")) > 0) throw new BadRequestException("السعر غير صحيح");
            c.setPrice(req.price());
        }
        if (req.status() != null) {
            if (!Set.of("ACTIVE", "HIDDEN").contains(req.status())) throw new BadRequestException("الحالة غير صحيحة");
            c.setStatus(req.status());
        }
        courses.save(c);
        if ("ACTIVE".equals(c.getStatus()) && !before.endsWith("/ACTIVE"))
            events.publishEvent(new com.manarah.common.events.DomainEvents.CourseOffered(c.getTenantId(), c.getId()));
        audit.record(actor, "COURSE_UPDATED_BY_ADMIN", "Course", c.getId(), before, c.getPrice() + "/" + c.getStatus());
        return row(c, a);
    }

    private CourseRow row(Course c, TeacherAcademy a) {
        long enrolled = enrollments.findByTenantIdAndCourseId(c.getTenantId(), c.getId()).stream()
                .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).count();
        return new CourseRow(c.getId(), a.getId(), a.getName(), c.getTitle(), Objects.toString(c.getGrade(), ""), c.getPrice(),
                c.getDiscountPercent(), c.getFinalPrice(), c.getStatus(), enrolled, Objects.toString(c.getCoverUrl(), ""));
    }

    // ---- Scope ------------------------------------------------------------------------------------------------

    private List<TeacherAcademy> managed(UserPrincipal actor) {
        bundleService.requireHeadOffice(actor);
        return academies.findByManagerTenantId(actor.getTenantId()).stream()
                .sorted(Comparator.comparing(TeacherAcademy::getId)).toList();
    }

    private TeacherAcademy managedAcademy(UserPrincipal actor, Long academyId) {
        return managed(actor).stream().filter(a -> a.getId().equals(academyId)).findFirst()
                .orElseThrow(() -> new ForbiddenException("مساحة المدرس دي تابعة لإدارة تانية"));
    }

    /** The account a student signs in with, provided they study with at least one teacher this head office manages. */
    private User managedStudent(UserPrincipal actor, Long userId) {
        var tenantIds = managed(actor).stream().map(TeacherAcademy::getTenantId).toList();
        User u = users.findById(userId).orElseThrow(() -> NotFoundException.of("الطالب", userId));
        if (u.getRole() != Role.STUDENT) throw new BadRequestException("الحساب ده مش حساب طالب");
        User owner = u.getPrimaryUserId() == null ? u : users.findById(u.getPrimaryUserId()).orElseThrow();
        boolean inScope = tenantIds.contains(owner.getTenantId())
                || users.findByPrimaryUserId(owner.getId()).stream().anyMatch(x -> tenantIds.contains(x.getTenantId()));
        if (!inScope) throw new ForbiddenException("الطالب ده مش تابع لمدرسين إدارتك");
        return owner;
    }
}
