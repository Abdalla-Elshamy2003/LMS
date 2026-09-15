package com.manarah.exam.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A reusable question in the question bank (§10). */
@Entity
@Table(name = "questions")
@Getter
@Setter
public class Question extends BaseEntity {

    private String subject;
    private String chapter;
    private String lesson;

    /** EASY, MEDIUM, HARD */
    @Column(nullable = false)
    private String difficulty = "MEDIUM";

    /** MCQ, TRUE_FALSE, MULTI_SELECT, FILL_BLANK, SHORT_ANSWER, ESSAY, NUMERIC */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Column(nullable = false)
    private double points = 1;

    /** Expected answer for FILL_BLANK / NUMERIC / SHORT_ANSWER auto-grading. */
    @Column(name = "correct_answer")
    private String correctAnswer;

    @Column(name = "learning_objective")
    private String learningObjective;

    private String tags;

    /** Shown to the student in the post-exam review when the exam allows correct answers. */
    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** Storage key of an image shown above the stem (diagram, graph, reading passage photo). */
    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
