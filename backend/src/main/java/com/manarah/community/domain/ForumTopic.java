package com.manarah.community.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A discussion topic in the student/teacher community forum (§ community). Null courseId means
 *  a general (non-course-specific) topic, visible to everyone in the tenant. */
@Entity
@Table(name = "forum_topics")
@Getter
@Setter
public class ForumTopic extends BaseEntity {

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "reply_count", nullable = false)
    private int replyCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt = Instant.now();
}
