package com.manarah.exam.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** An exam assembled from question-bank items, with security options (§9, §11, §12). */
@Entity
@Table(name = "exams")
@Getter
@Setter
public class Exam extends BaseEntity {

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    @Column(name = "total_points", nullable = false)
    private double totalPoints;

    @Column(name = "pass_percent", nullable = false)
    private double passPercent = 50;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions = true;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions = true;

    @Column(nullable = false)
    private boolean fullscreen;

    @Column(name = "disable_copy", nullable = false)
    private boolean disableCopy;

    @Column(name = "detect_tab_switch", nullable = false)
    private boolean detectTabSwitch;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    /** DRAFT, PUBLISHED, CLOSED */
    @Column(nullable = false)
    private String status = "DRAFT";

    /** Optional uploaded PDF exam (teacher uploads a paper instead of / besides bank questions). */
    @Column(name = "pdf_key")
    private String pdfKey;

    /** NEVER, AFTER_SUBMIT, AFTER_CLOSE — when a student may review their own answers. */
    @Column(name = "show_results", nullable = false)
    private String showResults = "AFTER_SUBMIT";

    @Column(name = "show_correct_answers", nullable = false)
    private boolean showCorrectAnswers = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
