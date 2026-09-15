package com.manarah.community.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One reply in a forum topic's thread (§ community). */
@Entity
@Table(name = "forum_replies")
@Getter
@Setter
public class ForumReply extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
