package com.manarah.communication.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "support_cases")
@Getter
@Setter
public class SupportCase extends BaseEntity {
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;
    @Column(name = "student_id")
    private Long studentId;
    @Column(name = "course_id")
    private Long courseId;
    @Column(name = "assigned_teacher_id")
    private Long assignedTeacherId;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String priority = "NORMAL";
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt = Instant.now();
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
