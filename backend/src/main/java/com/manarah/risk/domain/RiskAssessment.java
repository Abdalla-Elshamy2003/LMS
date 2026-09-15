package com.manarah.risk.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A point-in-time risk assessment for a student (§17). History is kept; latest drives alerts. */
@Entity
@Table(name = "risk_assessments")
@Getter
@Setter
public class RiskAssessment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** NONE, LOW, MEDIUM, HIGH, AT_RISK */
    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private double score;

    /** JSON array of human-readable reasons. */
    @Column(columnDefinition = "TEXT")
    private String reasons;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt = Instant.now();
}
