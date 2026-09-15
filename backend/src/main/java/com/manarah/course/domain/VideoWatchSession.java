package com.manarah.course.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One device watching one protected lesson video. The server allows a bounded number of live
 * sessions per account (default one), so a shared login cannot stream on several devices at once,
 * and every session leaves an IP/device trail staff can review.
 */
@Entity
@Table(name = "video_watch_sessions")
@Getter
@Setter
public class VideoWatchSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "session_token", nullable = false, unique = true)
    private String sessionToken;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    /** CLOSED, REPLACED, EXPIRED */
    @Column(name = "end_reason")
    private String endReason;

    @Column(name = "watched_seconds", nullable = false)
    private int watchedSeconds;
}
