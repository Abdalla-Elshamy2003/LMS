package com.manarah.gamification.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A badge awarded to a student (§30). */
@Entity
@Table(name = "student_badges")
@Getter
@Setter
public class StudentBadge extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "badge_id", nullable = false)
    private Long badgeId;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();
}
