package com.manarah.course.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A student's answer to one in-video checkpoint. Re-watching overwrites the previous attempt. */
@Entity
@Table(name = "lesson_checkpoint_answers")
@Getter
@Setter
public class LessonCheckpointAnswer extends BaseEntity {

    @Column(name = "checkpoint_id", nullable = false)
    private Long checkpointId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt = Instant.now();
}
