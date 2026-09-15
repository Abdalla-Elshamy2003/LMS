package com.manarah.security;

import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated principal, rebuilt from JWT claims on every request (stateless). */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final Long tenantId;
    private final Long branchId;
    private final String fullName;
    private final String email;
    private final Role role;

    public UserPrincipal(Long id, Long tenantId, Long branchId, String fullName, String email, Role role) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getTenantId(), user.getBranchId(),
                user.getFullName(), user.getEmail(), user.getRole());
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getBranchId() { return branchId; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }

    public boolean isAdmin() { return Role.ADMIN_ROLES.contains(role); }
    public boolean isStaff() { return Role.STAFF_ROLES.contains(role); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
