package com.manarah.timeline.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** An entry in a student's activity timeline (§18), appended by domain-event listeners. */
@Entity
@Table(name = "student_timeline")
@Getter
@Setter
public class TimelineEntry extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** ATTENDANCE, EXAM, HOMEWORK, PAYMENT, RISK, NOTE, ENROLLMENT */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail;

    private String icon;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
