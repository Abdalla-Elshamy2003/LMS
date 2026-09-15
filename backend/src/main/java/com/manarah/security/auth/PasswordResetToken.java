package com.manarah.security.auth;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One issued "forgot my password" link. Only the SHA-256 hash of the token is stored — the raw
 * value lives solely in the message that was sent, so the table is useless to anyone who reads it.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
public class PasswordResetToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    /** EMAIL or WHATSAPP — which channel actually carried the link. */
    @Column
    private String channel;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
