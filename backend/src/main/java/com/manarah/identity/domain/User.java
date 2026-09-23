package com.manarah.identity.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String username;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Teacher/staff profile (used on the public landing page and staff management). */
    private String subjects;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "photo_url")
    private String photoUrl;

    private String schedule;

    private String title;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * Set on the row a student gets when they join another teacher: the account they sign in with. Such a row has
     * a placeholder email and an unusable password; sessions for it live and die with the main account's.
     */
    @Column(name = "primary_user_id")
    private Long primaryUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
