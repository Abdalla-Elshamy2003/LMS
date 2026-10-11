package com.manarah.security.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Only keyed code hashes and opaque verification-proof hashes are persisted. */
@Entity @Table(name = "email_login_codes") @Getter @Setter
public class EmailLoginCode {
    @Id private String id;
    private String email;
    private String purpose;
    private Long userId;
    private String codeHash;
    private int attempts;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant deliveredAt;
    private Instant verifiedAt;
    private Instant usedAt;
    private String proofHash;
}
