package com.manarah.notification.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A configurable rule for the notifications engine (§34):
 * IF trigger [threshold] THEN notify parent/teacher over channels.
 */
@Entity
@Table(name = "notification_rules")
@Getter
@Setter
public class NotificationRule extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** STUDENT_ABSENT, LOW_SCORE, HOMEWORK_MISSED, RISK_ESCALATED, INSTALLMENT_DUE */
    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    private Double threshold;

    /** CSV of channels: IN_APP,WHATSAPP,SMS,EMAIL,PUSH */
    @Column(nullable = false)
    private String channels = "IN_APP";

    @Column(name = "notify_parent", nullable = false)
    private boolean notifyParent = true;

    @Column(name = "notify_teacher", nullable = false)
    private boolean notifyTeacher;

    @Column(columnDefinition = "TEXT")
    private String template;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
