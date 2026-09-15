package com.manarah.notification.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A notification delivered to a user through a channel (§4). */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private String category = "GENERAL";

    /** IN_APP, WHATSAPP, SMS, EMAIL, PUSH */
    @Column(nullable = false)
    private String channel = "IN_APP";

    /** PENDING, SENT, READ, FAILED */
    @Column(nullable = false)
    private String status = "SENT";

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
