package com.manarah.center;

import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.identity.LoginCredentials;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.domain.Branch;
import com.manarah.org.domain.Tenant;
import com.manarah.org.repo.BranchRepository;
import com.manarah.org.repo.TenantRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The head office's side of centers: open one (its own tenant, branch and CENTER_ADMIN sign-in, the same shape as a
 * teacher's space), change its sign-in, or lock it. What happens inside a center is the center's own business.
 */
@Service
public class CenterAdminService {
    private final CenterRepository centers;
    private final CenterTeacherRepository teachers;
    private final CenterStudentRepository students;
    private final TenantRepository tenants;
    private final BranchRepository branches;
    private final UserRepository users;
    private final TeacherAcademyRepository academies;
    private final LoginCredentials logins;
    private final com.manarah.audit.AuditService audit;

    public CenterAdminService(CenterRepository centers, CenterTeacherRepository teachers, CenterStudentRepository students,
                              TenantRepository tenants, BranchRepository branches, UserRepository users,
                              TeacherAcademyRepository academies, LoginCredentials logins, com.manarah.audit.AuditService audit) {
        this.centers = centers; this.teachers = teachers; this.students = students; this.tenants = tenants; this.branches = branches;
        this.users = users; this.academies = academies; this.logins = logins; this.audit = audit;
    }

    public record CenterRow(Long id, String name, String username, String phone, String address, boolean active,
                            long teachers, long students, Instant createdAt) {}
    public record CreateCenter(String name, String username, String password, String phone, String address) {}
    public record Credentials(String username, String password) {}

    public List<CenterRow> list(UserPrincipal actor) {
        requireHeadOffice(actor);
        return centers.findByManagerTenantIdOrderByIdDesc(actor.getTenantId()).stream().map(this::row).toList();
    }

    @Transactional
    public CenterRow create(UserPrincipal actor, CreateCenter req) {
        requireHeadOffice(actor);
        String name = CenterService.required(req.name(), 120, "اسم السنتر");
        User u = new User();
        String username = logins.username(u, req.username());
        Tenant t = new Tenant();
        t.setName(name);
        t.setSlug(freeSlug(username));
        tenants.save(t);
        Branch b = new Branch();
        b.setTenantId(t.getId());
        b.setName(name);
        branches.save(b);
        u.setTenantId(t.getId());
        u.setBranchId(b.getId());
        u.setFullName(name);
        u.setEmail(UUID.randomUUID() + "@accounts.local");
        u.setRole(Role.CENTER_ADMIN);
        logins.assign(u, username, req.password());
        users.save(u);
        Center c = new Center();
        c.setTenantId(t.getId());
        c.setManagerTenantId(actor.getTenantId());
        c.setOwnerUserId(u.getId());
        c.setName(name);
        c.setSlug(t.getSlug());
        c.setPhone(CenterService.optional(req.phone(), 40));
        c.setAddress(CenterService.optional(req.address(), 250));
        centers.save(c);
        audit.record(actor, "CENTER_CREATED", "Center", c.getId(), null, c.getName() + "/" + u.getUsername());
        return row(c);
    }

    @Transactional
    public CenterRow credentials(UserPrincipal actor, Long id, Credentials req) {
        Center c = managed(actor, id);
        User u = users.findById(c.getOwnerUserId()).orElseThrow();
        if (req.password() == null || req.password().isBlank()) u.setUsername(logins.username(u, req.username()));
        else logins.assign(u, req.username(), req.password());
        users.save(u);
        audit.record(actor, "CENTER_CREDENTIALS_CHANGED", "Center", c.getId(), null, "username=" + u.getUsername());
        return row(c);
    }

    /** Locking a center signs it out at once: every request re-reads the account and needs it ACTIVE. */
    @Transactional
    public CenterRow setActive(UserPrincipal actor, Long id, boolean active) {
        Center c = managed(actor, id);
        User u = users.findById(c.getOwnerUserId()).orElseThrow();
        String before = u.getStatus();
        u.setStatus(active ? "ACTIVE" : "INACTIVE");
        users.save(u);
        audit.record(actor, active ? "CENTER_ACTIVATED" : "CENTER_LOCKED", "Center", c.getId(), before, u.getStatus());
        return row(c);
    }

    private CenterRow row(Center c) {
        User u = users.findById(c.getOwnerUserId()).orElse(null);
        return new CenterRow(c.getId(), c.getName(), u == null ? null : u.getUsername(), c.getPhone(), c.getAddress(),
                u != null && "ACTIVE".equals(u.getStatus()), teachers.countByTenantId(c.getTenantId()),
                students.countByTenantIdAndActiveTrue(c.getTenantId()), c.getCreatedAt());
    }

    private Center managed(UserPrincipal actor, Long id) {
        requireHeadOffice(actor);
        Center c = centers.findById(id).orElseThrow(() -> NotFoundException.of("السنتر", id));
        if (!c.getManagerTenantId().equals(actor.getTenantId())) throw new ForbiddenException("السنتر ده تابع لإدارة تانية");
        return c;
    }

    /** Centers are opened by the head office — never from inside a teacher's space or another center. */
    private void requireHeadOffice(UserPrincipal actor) {
        if (!actor.isAdmin() || academies.findByTenantId(actor.getTenantId()).isPresent()
                || centers.findByTenantId(actor.getTenantId()).isPresent())
            throw new ForbiddenException("السناتر بتتدار من الإدارة الرئيسية بس");
    }

    private String freeSlug(String username) {
        String base = "center-" + username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String slug = base;
        for (int n = 2; tenants.existsBySlug(slug) || centers.existsBySlug(slug); n++) slug = base + "-" + n;
        return slug;
    }
}
