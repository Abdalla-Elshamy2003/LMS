package com.manarah.student.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A parent / guardian (§3). May optionally have a login user account. */
@Entity
@Table(name = "guardians")
@Getter
@Setter
public class Guardian extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
