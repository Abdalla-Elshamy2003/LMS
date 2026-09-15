package com.manarah.homework.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A student's submission for an assignment (§14). */
@Entity
@Table(name = "submissions")
@Getter
@Setter
public class Submission extends BaseEntity {
    @Version private long version;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "file_key")
    private String fileKey;

    /** SUBMITTED, LATE, MISSING, GRADED */
    @Column(nullable = false)
    private String status = "SUBMITTED";

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private Double score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_by")
    private Long gradedBy;

    @Column(name = "graded_at")
    private Instant gradedAt;

    /** Score before any late penalty; {@code score} is what the gradebook receives. */
    @Column(name = "raw_score")
    private Double rawScore;

    @Column(name = "penalty_percent", nullable = false)
    private double penaltyPercent;

    /** JSON {criterionId: points} when the assignment has a rubric. */
    @Column(name = "rubric_scores", columnDefinition = "TEXT")
    private String rubricScores;

    @Column(nullable = false)
    private int resubmissions;

    @Column(name = "returned_at")
    private Instant returnedAt;
}
