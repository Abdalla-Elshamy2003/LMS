package com.manarah.identity;

import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final PasswordEncoder passwordEncoder;
    private final com.manarah.audit.AuditService audit;
    private final com.manarah.academy.TeacherAcademyRepository academies;

    public UserService(UserRepository users, CourseRepository courses, EnrollmentRepository enrollments,
                       PasswordEncoder passwordEncoder, com.manarah.academy.TeacherAcademyRepository academies, com.manarah.audit.AuditService audit) {
        this.academies = academies;
        this.users = users;
        this.courses = courses;
        this.enrollments = enrollments;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public record UserSummary(Long id, String fullName, String email, String phone, String role, String roleArabic,
                              String status, Long branchId, String subjects, String bio, String photoUrl,
                              String schedule, String title) {
    }

    public record CreateUserRequest(String fullName, String email, String phone, String password, String role,
                                    Long branchId, String subjects, String bio, String photoUrl, String schedule,
                                    String title) {
    }

    /** {@code academyId}/{@code academySlug} are set only for teachers who have their own academy
     *  space — the staff page uses them to link straight into that workspace. Null otherwise. */
    public record TeacherDetail(Long id, String fullName, String email, String phone, String photoUrl,
                                String subjects, String bio, String schedule, String title,
                                long totalStudents, List<Map<String, Object>> courses,
                                Long academyId, String academyName, String academySlug) {
    }
    public record SelfProfile(Long id, String fullName, String email, String phone, String role, String roleArabic,
                              String photoUrl, String title, String subjects, String bio, String schedule) {}
    public record SelfUpdateRequest(String fullName, String phone, String photoUrl, String title,
                                    String subjects, String bio, String schedule) {}
    public record PasswordRequest(String currentPassword, String newPassword) {}

    public SelfProfile me(UserPrincipal actor) {
        User u = users.findByTenantIdAndId(actor.getTenantId(), actor.getId()).orElseThrow(() -> NotFoundException.of("المستخدم", actor.getId()));
        return toSelf(u);
    }

    @Transactional
    public SelfProfile updateSelf(UserPrincipal actor, SelfUpdateRequest req) {
        User u = users.findByTenantIdAndId(actor.getTenantId(), actor.getId()).orElseThrow(() -> NotFoundException.of("المستخدم", actor.getId()));
        if (req.fullName() != null && !req.fullName().isBlank()) u.setFullName(req.fullName().trim());
        if (req.phone() != null) u.setPhone(req.phone().trim());
        if (req.photoUrl() != null) u.setPhotoUrl(req.photoUrl().trim());
        if (u.getRole() == Role.TEACHER || u.getRole() == Role.ASSISTANT) {
            if (req.title() != null) u.setTitle(req.title().trim());
            if (req.subjects() != null) u.setSubjects(req.subjects().trim());
            if (req.bio() != null) u.setBio(req.bio().trim());
            if (req.schedule() != null) u.setSchedule(req.schedule().trim());
        }
        users.save(u);
        return toSelf(u);
    }

    @Transactional
    public void changePassword(UserPrincipal actor, PasswordRequest req) {
        PasswordPolicy.requireStrong(req.newPassword());
        User u = users.findByTenantIdAndId(actor.getTenantId(), actor.getId()).orElseThrow(() -> NotFoundException.of("المستخدم", actor.getId()));
        // A student inside another teacher's space still signs in with their own account — that is the password
        // being changed, and changing it ends their sessions with every teacher.
        if (u.getPrimaryUserId() != null)
            u = users.findById(u.getPrimaryUserId()).orElseThrow(() -> NotFoundException.of("المستخدم", actor.getId()));
        if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), u.getPasswordHash()))
            throw new BadRequestException("كلمة المرور الحالية غير صحيحة");
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(u);
        audit.record(actor, "PASSWORD_CHANGED", "User", u.getId(), null, "credentials rotated");
    }

    public List<UserSummary> staff() {
        return users.findByTenantId(TenantContext.require(), org.springframework.data.domain.Pageable.unpaged())
                .stream().map(this::toSummary).toList();
    }

    public List<UserSummary> byRole(String role) {
        return users.findByTenantIdAndRole(TenantContext.require(), Role.valueOf(role)).stream().map(this::toSummary).toList();
    }

    /** Teachers with what they teach, their schedule and how many students — for staff management.
     *  Spans the caller's own tenant plus every academy tenant they manage, so a teacher created as
     *  their own academy space shows up here automatically instead of only inside that workspace. */
    public List<TeacherDetail> teachersDetail() {
        List<Long> tenantIds = academies.visibleTenantIds(TenantContext.require());
        return users.findByTenantIdInAndRole(tenantIds, Role.TEACHER).stream().map(u -> {
            var taught = courses.findByTenantIdAndTeacherId(u.getTenantId(), u.getId());
            long total = taught.stream().mapToLong(c -> enrollments.countStudying(c.getTenantId(), c.getId())).sum();
            var courseList = taught.stream().map(c -> Map.<String, Object>of(
                    "id", c.getId(), "title", c.getTitle(), "subject", c.getSubject() == null ? "" : c.getSubject(),
                    "schedule", c.getSchedule() == null ? "" : c.getSchedule(),
                    "students", enrollments.countStudying(c.getTenantId(), c.getId()))).toList();
            var academy = academies.findByTenantId(u.getTenantId()).orElse(null);
            return new TeacherDetail(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getPhotoUrl(),
                    u.getSubjects(), u.getBio(), u.getSchedule(), u.getTitle(), total, courseList,
                    academy == null ? null : academy.getId(),
                    academy == null ? null : academy.getName(),
                    academy == null ? null : academy.getSlug());
        }).toList();
    }

    @Transactional
    public UserSummary create(UserPrincipal actor, CreateUserRequest req) {
        Long tenantId = TenantContext.require();
        if (academies.findByTenantId(tenantId).isPresent() && "TEACHER".equals(req.role()))
            throw new BadRequestException("هذه المساحة مخصصة لمدرس واحد. أنشئ مساحة مستقلة للمدرس الجديد من الإدارة الرئيسية.");
        if (users.existsByTenantIdAndEmailIgnoreCase(tenantId, req.email())) {
            throw new ConflictException("البريد الإلكتروني مستخدم بالفعل");
        }
        User u = new User();
        u.setTenantId(tenantId);
        apply(u, req);
        u.setPasswordHash(passwordEncoder.encode(PasswordPolicy.requireStrong(req.password())));
        users.save(u);
        return toSummary(u);
    }

    @Transactional
    public UserSummary update(Long id, CreateUserRequest req) {
        Long tenantId = TenantContext.require();
        User u = users.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("المستخدم", id));
        academies.findByTenantId(tenantId).ifPresent(a -> {
            if (req.role() != null && (("TEACHER".equals(req.role()) && !a.getTeacherId().equals(id))
                || (a.getTeacherId().equals(id) && !"TEACHER".equals(req.role()))))
                throw new BadRequestException("لا يمكن تغيير مدرس هذه المساحة أو إضافة مدرس آخر إليها");
        });
        apply(u, req);
        users.save(u);
        return toSummary(u);
    }

    private void apply(User u, CreateUserRequest req) {
        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.email() != null) u.setEmail(req.email());
        if (req.phone() != null) u.setPhone(req.phone());
        if (req.role() != null) u.setRole(Role.valueOf(req.role()));
        if (req.branchId() != null) u.setBranchId(req.branchId());
        if (req.subjects() != null) u.setSubjects(req.subjects());
        if (req.bio() != null) u.setBio(req.bio());
        if (req.photoUrl() != null) u.setPhotoUrl(req.photoUrl());
        if (req.schedule() != null) u.setSchedule(req.schedule());
        if (req.title() != null) u.setTitle(req.title());
    }

    /** A student's seat with another teacher carries a placeholder address; the real one is on the account they sign in with. */
    private String displayEmail(User u) {
        if (u.getPrimaryUserId() == null) return u.getEmail();
        return users.findById(u.getPrimaryUserId()).map(User::getEmail).orElse(u.getEmail());
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getFullName(), displayEmail(u), u.getPhone(), u.getRole().name(),
                u.getRole().getArabicName(), u.getStatus(), u.getBranchId(), u.getSubjects(), u.getBio(),
                u.getPhotoUrl(), u.getSchedule(), u.getTitle());
    }

    private SelfProfile toSelf(User u) {
        return new SelfProfile(u.getId(), u.getFullName(), displayEmail(u), u.getPhone(), u.getRole().name(),
                u.getRole().getArabicName(), u.getPhotoUrl(), u.getTitle(), u.getSubjects(), u.getBio(), u.getSchedule());
    }
}
