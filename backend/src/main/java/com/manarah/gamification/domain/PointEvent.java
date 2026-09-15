package com.manarah.gamification.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A points award/deduction event (§31). */
@Entity
@Table(name = "point_events")
@Getter
@Setter
public class PointEvent extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private int points;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
