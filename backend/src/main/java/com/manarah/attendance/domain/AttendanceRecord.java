package com.manarah.attendance.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One student's attendance for one session (§5). */
@Entity
@Table(name = "attendance_records")
@Getter
@Setter
public class AttendanceRecord extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** PRESENT, ABSENT, LATE, EXCUSED */
    @Column(nullable = false)
    private String status;

    @Column(name = "arrival_time")
    private Instant arrivalTime;

    @Column(name = "leave_time")
    private Instant leaveTime;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes;

    private String reason;

    /** MANUAL, QR */
    @Column(nullable = false)
    private String method = "MANUAL";

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
