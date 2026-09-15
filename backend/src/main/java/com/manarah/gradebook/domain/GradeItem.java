package com.manarah.gradebook.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A single gradebook entry (§15). Written by the gradebook listener whenever an exam or homework
 * is graded, plus manual items — a unified ledger the gradebook and analytics read from.
 */
@Entity
@Table(name = "grade_items")
@Getter
@Setter
public class GradeItem extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false)
    private String title;

    /** QUIZ, EXAM, HOMEWORK, MIDTERM, FINAL, OTHER */
    @Column(nullable = false)
    private String category = "OTHER";

    @Column(nullable = false)
    private double score;

    @Column(name = "max_score", nullable = false)
    private double maxScore;

    @Column(nullable = false)
    private double weight = 1;

    /** EXAM, HOMEWORK, MANUAL */
    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();
}
