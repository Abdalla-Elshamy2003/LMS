package com.manarah.attendance.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A single class session for which attendance is taken (§5). Carries a rotating QR token. */
@Entity
@Table(name = "class_sessions")
@Getter
@Setter
public class ClassSession extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(nullable = false)
    private String title;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "qr_token")
    private String qrToken;

    @Column(name = "qr_expires_at")
    private Instant qrExpiresAt;

    /** SCHEDULED, OPEN, CLOSED */
    @Column(nullable = false)
    private String status = "SCHEDULED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
