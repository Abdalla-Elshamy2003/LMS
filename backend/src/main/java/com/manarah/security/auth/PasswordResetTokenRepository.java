package com.manarah.security.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Looked up without a tenant filter on purpose: the person clicking the link isn't logged in
     *  yet, so there is no tenant in context — the token itself is the only credential. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);
}
