package com.manarah.communication.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "support_messages")
@Getter
@Setter
public class SupportMessage extends BaseEntity {
    @Column(name = "case_id", nullable = false)
    private Long caseId;
    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
