package com.manarah.evaluation.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A student's evaluation of a teacher (§19), optionally anonymous. */
@Entity
@Table(name = "teacher_evaluations")
@Getter
@Setter
public class TeacherEvaluation extends BaseEntity {

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "student_id")
    private Long studentId;

    private Integer explanation;
    private Integer engagement;

    @Column(name = "content_quality")
    private Integer contentQuality;

    private Integer difficulty;
    private Double overall;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private boolean anonymous = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
