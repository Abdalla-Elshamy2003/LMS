package com.manarah.identity.repo;

import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    /** Email is unique per tenant only, so the same address can exist in several teacher spaces. */
    List<User> findAllByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** The rows a student got in other teacher spaces, linked to the account they sign in with. */
    List<User> findByPrimaryUserId(Long primaryUserId);

    Optional<User> findByTenantIdAndId(Long tenantId, Long id);

    List<User> findByTenantIdAndRole(Long tenantId, Role role);

    /** Rolls up managed academy tenants alongside the caller's own — see AcademyAccess.visibleTenantIds. */
    List<User> findByTenantIdInAndRole(List<Long> tenantIds, Role role);

    Page<User> findByTenantId(Long tenantId, Pageable pageable);

    Page<User> findByTenantIdAndRole(Long tenantId, Role role, Pageable pageable);

    long countByTenantIdAndRole(Long tenantId, Role role);

    long countByTenantIdInAndRole(List<Long> tenantIds, Role role);

    boolean existsByTenantIdAndEmailIgnoreCase(Long tenantId, String email);
}
