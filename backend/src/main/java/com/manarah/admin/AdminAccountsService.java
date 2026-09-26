package com.manarah.admin;

import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.center.CenterScope;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.identity.LoginCredentials;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.repo.BranchRepository;
import com.manarah.security.PasswordPolicy;
import com.manarah.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The platform's admins, kept in the database like every other account: a super admin of the head office adds more
 * admins (username sign-in), resets their passwords, and locks or reopens them. Nobody can lock themselves, and the
 * last active super admin can never be locked, so the platform always keeps an owner.
 */
@Service
public class AdminAccountsService {
    private final UserRepository users;
    private final BranchRepository branches;
    private final TeacherAcademyRepository academies;
    private final CenterScope centers;
    private final LoginCredentials logins;
    private final PasswordEncoder passwords;
    private final com.manarah.audit.AuditService audit;

    public AdminAccountsService(UserRepository users, BranchRepository branches, TeacherAcademyRepository academies, CenterScope centers,
                                LoginCredentials logins, PasswordEncoder passwords, com.manarah.audit.AuditService audit) {
        this.users = users; this.branches = branches; this.academies = academies; this.centers = centers; this.logins = logins;
        this.passwords = passwords; this.audit = audit;
    }

    public record AdminRow(Long id, String fullName, String username, String role, String roleArabic, String status,
                           Instant lastLoginAt, Instant createdAt, boolean you) {}
    public record CreateAdmin(String fullName, String username, String password) {}

    public List<AdminRow> list(UserPrincipal actor) {
        requireOwner(actor);
        return admins(actor.getTenantId())
                .sorted(Comparator.comparing((User u) -> !"ACTIVE".equals(u.getStatus())).thenComparing(User::getId))
                .map(u -> row(u, actor)).toList();
    }

    @Transactional
    public AdminRow create(UserPrincipal actor, CreateAdmin req) {
        requireOwner(actor);
        if (req.fullName() == null || req.fullName().isBlank() || req.fullName().trim().length() > 120)
            throw new BadRequestException("اكتب اسم الأدمن");
        User u = new User();
        u.setTenantId(actor.getTenantId());
        u.setBranchId(actor.getBranchId() != null ? actor.getBranchId()
                : branches.findByTenantIdOrderByName(actor.getTenantId()).stream().findFirst().map(b -> b.getId()).orElse(null));
        u.setFullName(req.fullName().trim());
        String username = logins.username(u, req.username());
        String email = username + "@control.manarah.local";
        if (users.existsByTenantIdAndEmailIgnoreCase(actor.getTenantId(), email)) throw new ConflictException("اسم المستخدم مستخدم بالفعل");
        u.setEmail(email);
        u.setRole(Role.SUPER_ADMIN);
        logins.assign(u, username, req.password());
        users.save(u);
        audit.record(actor, "ADMIN_CREATED", "User", u.getId(), null, u.getUsername());
        return row(u, actor);
    }

    @Transactional
    public AdminRow setActive(UserPrincipal actor, Long id, boolean active) {
        requireOwner(actor);
        User u = admin(actor, id);
        if (!active) {
            if (u.getId().equals(actor.getId())) throw new BadRequestException("مينفعش توقف حسابك انت");
            long otherOwners = admins(actor.getTenantId())
                    .filter(x -> x.getRole() == Role.SUPER_ADMIN && "ACTIVE".equals(x.getStatus()) && !x.getId().equals(u.getId())).count();
            if (u.getRole() == Role.SUPER_ADMIN && otherOwners == 0) throw new BadRequestException("لازم يفضل أدمن رئيسي واحد شغال على الأقل");
        }
        String before = u.getStatus();
        u.setStatus(active ? "ACTIVE" : "INACTIVE");
        users.save(u);
        audit.record(actor, active ? "ADMIN_ACTIVATED" : "ADMIN_LOCKED", "User", u.getId(), before, u.getStatus());
        return row(u, actor);
    }

    /** Another admin's password; your own goes through the profile, which asks for the current one. */
    @Transactional
    public AdminRow resetPassword(UserPrincipal actor, Long id, String password) {
        requireOwner(actor);
        User u = admin(actor, id);
        if (u.getId().equals(actor.getId())) throw new BadRequestException("غيّر باسوردك انت من صفحة الملف الشخصي");
        u.setPasswordHash(passwords.encode(PasswordPolicy.requireStrong(password)));
        users.save(u);
        audit.record(actor, "ADMIN_PASSWORD_RESET", "User", u.getId(), null, "credentials rotated");
        return row(u, actor);
    }

    private Stream<User> admins(Long tenantId) {
        return Role.ADMIN_ROLES.stream().flatMap(r -> users.findByTenantIdAndRole(tenantId, r).stream());
    }

    private User admin(UserPrincipal actor, Long id) {
        User u = users.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("الأدمن", id));
        if (!Role.ADMIN_ROLES.contains(u.getRole())) throw new BadRequestException("الحساب ده مش حساب أدمن");
        return u;
    }

    /** Only a super admin of the head office (not a teacher's space, not a center) manages the platform's admins. */
    private void requireOwner(UserPrincipal actor) {
        if (actor.getRole() != Role.SUPER_ADMIN || academies.findByTenantId(actor.getTenantId()).isPresent()
                || centers.isCenterTenant(actor.getTenantId()))
            throw new ForbiddenException("إدارة الأدمنز متاحة للأدمن الرئيسي بس");
    }

    private AdminRow row(User u, UserPrincipal actor) {
        return new AdminRow(u.getId(), u.getFullName(), u.getUsername() != null ? u.getUsername() : u.getEmail(), u.getRole().name(),
                u.getRole().getArabicName(), u.getStatus(), u.getLastLoginAt(), u.getCreatedAt(), Objects.equals(u.getId(), actor.getId()));
    }
}
