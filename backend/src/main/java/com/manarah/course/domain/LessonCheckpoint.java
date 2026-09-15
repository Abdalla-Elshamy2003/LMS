package com.manarah.course.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A bank question pinned to a moment in a lesson video: the player pauses at {@code atSeconds} and
 * asks it before letting the student carry on. {@code atSeconds == null} marks an end-of-lesson test
 * question instead, shown once the video finishes.
 */
@Entity
@Table(name = "lesson_checkpoints")
@Getter
@Setter
public class LessonCheckpoint extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "at_seconds")
    private Integer atSeconds;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
