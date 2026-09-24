package com.manarah.admin;

import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.repo.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;

/**
 * Creates the platform owner's head-office admin from two environment variables — a username and a BCrypt hash, so
 * the password itself is never stored anywhere but in the owner's hands (the repository is public).
 *
 * <p>Create-only: if the username already exists nothing happens, so the variables can never be used to take over
 * or reset an account. Once the admin has signed in and changed the password, the variables should be removed.
 */
@Component
public class AdminBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository users;
    private final TeacherAcademyRepository academies;
    private final BranchRepository branches;
    private final String username;
    private final String passwordHash;

    public AdminBootstrap(UserRepository users, TeacherAcademyRepository academies, BranchRepository branches,
                          @Value("${manarah.bootstrap.admin.username:}") String username,
                          @Value("${manarah.bootstrap.admin.password-hash:}") String passwordHash) {
        this.users = users; this.academies = academies; this.branches = branches;
        this.username = username == null ? "" : username.trim();
        this.passwordHash = passwordHash == null ? "" : passwordHash.trim();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (username.isEmpty() || passwordHash.isEmpty()) return;
        createIfMissing(username, passwordHash);
    }

    /** @return true when an account was created, false when the username already existed or the input was refused */
    @Transactional
    public boolean createIfMissing(String login, String hash) {
        String name = login.toLowerCase(java.util.Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9._-]{2,49}")) { log.warn("Bootstrap admin skipped: invalid username"); return false; }
        if (!hash.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$")) { log.warn("Bootstrap admin skipped: the password hash is not BCrypt"); return false; }
        if (users.findByUsernameIgnoreCase(name).isPresent()) { log.info("Bootstrap admin '{}' already exists; left unchanged", name); return false; }
        Optional<Long> headOffice = academies.findAll().stream().map(TeacherAcademy::getManagerTenantId).findFirst()
                .or(() -> users.findAll().stream().filter(u -> u.getRole() == Role.SUPER_ADMIN)
                        .min(Comparator.comparing(User::getId)).map(User::getTenantId));
        if (headOffice.isEmpty()) { log.warn("Bootstrap admin skipped: no head office to put it in"); return false; }
        Long tenantId = headOffice.get();
        User u = new User();
        u.setTenantId(tenantId);
        u.setBranchId(branches.findByTenantIdOrderByName(tenantId).stream().findFirst().map(b -> b.getId()).orElse(null));
        u.setFullName("مدير المنصة");
        u.setUsername(name);
        u.setEmail(name + "@control.manarah.local");
        u.setRole(Role.SUPER_ADMIN);
        u.setStatus("ACTIVE");
        u.setPasswordHash(hash);
        users.save(u);
        log.info("Bootstrap admin '{}' created in tenant {}", name, tenantId);
        return true;
    }
}
