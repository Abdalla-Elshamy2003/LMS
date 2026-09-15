package com.manarah.org.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A classroom / room with capacity and facilities (§23). */
@Entity
@Table(name = "rooms")
@Getter
@Setter
public class Room extends BaseEntity {

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity = 30;

    @Column(name = "has_projector", nullable = false)
    private boolean hasProjector;

    @Column(name = "has_ac", nullable = false)
    private boolean hasAc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
