package com.manarah.community.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Tracks which user liked which topic, so a like toggles cleanly instead of double-counting. */
@Entity
@Table(name = "forum_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "user_id"}))
@Getter
@Setter
public class ForumLike extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
