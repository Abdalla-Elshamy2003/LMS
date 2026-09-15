package com.manarah.gamification.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Aggregate points & level for a student (§30). */
@Entity
@Table(name = "student_points")
@Getter
@Setter
public class StudentPoints extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private int points;

    /** BRONZE, SILVER, GOLD, PLATINUM, DIAMOND */
    @Column(nullable = false)
    private String level = "BRONZE";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static String levelFor(int points) {
        if (points >= 5000) return "DIAMOND";
        if (points >= 2500) return "PLATINUM";
        if (points >= 1000) return "GOLD";
        if (points >= 400) return "SILVER";
        return "BRONZE";
    }
}
