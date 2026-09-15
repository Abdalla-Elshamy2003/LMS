package com.manarah.student.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One scan of a student's personal pass at the door — an arrival or a departure. */
@Entity
@Table(name = "student_gate_logs")
@Getter
@Setter
public class StudentGateLog extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** IN or OUT — decided by the service from the student's last scan today, not by the caller. */
    @Column(nullable = false)
    private String direction;

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @Column(nullable = false)
    private Instant at = Instant.now();
}
