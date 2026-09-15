package com.manarah.homework.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A homework assignment (§14). */
@Entity
@Table(name = "assignments")
@Getter
@Setter
public class Assignment extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** An optional worksheet/handout the teacher attaches to the assignment itself — distinct
     *  from a student's own submission file. */
    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "start_at")
    private Instant startAt;

    private Instant deadline;

    @Column(name = "max_score", nullable = false)
    private double maxScore = 100;

    @Column(name = "allow_late", nullable = false)
    private boolean allowLate = true;

    /** Percent of the raw score deducted per day late (Canvas-style); 0 disables. */
    @Column(name = "late_penalty_percent", nullable = false)
    private double latePenaltyPercent;

    /** JSON rubric: [{id, title, description, maxPoints, levels:[{label, points, description}]}]. */
    @Column(columnDefinition = "TEXT")
    private String rubric;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
