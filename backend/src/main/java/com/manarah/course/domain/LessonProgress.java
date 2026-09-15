package com.manarah.course.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Per-student watch/open tracking for a lesson — powers the engagement insights (§7). */
@Entity
@Table(name = "lesson_progress")
@Getter
@Setter
public class LessonProgress extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "watched_seconds", nullable = false)
    private int watchedSeconds;

    @Column(name = "last_position", nullable = false)
    private int lastPosition;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private int views;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
