package com.manarah.org.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A physical branch of an academy (§22). */
@Entity
@Table(name = "branches")
@Getter
@Setter
public class Branch extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String address;
    private String phone;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
