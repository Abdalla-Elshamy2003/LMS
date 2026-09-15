package com.manarah.exam.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A student's attempt at an exam (§9, §12). Holds the per-student randomised question order. */
@Entity
@Table(name = "student_exams")
@Getter
@Setter
public class StudentExam extends BaseEntity {
    @Version private long version;
    @Column(name = "draft_answers", columnDefinition = "TEXT") private String draftAnswers;
    @Column(name = "draft_saved_at") private Instant draftSavedAt;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** NOT_STARTED, IN_PROGRESS, SUBMITTED, GRADED */
    @Column(nullable = false)
    private String status = "NOT_STARTED";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(nullable = false)
    private double score;

    @Column(name = "max_score", nullable = false)
    private double maxScore;

    @Column(name = "tab_switches", nullable = false)
    private int tabSwitches;

    /** JSON array of question ids in this student's order (anti-cheating, §11). */
    @Column(name = "question_order", columnDefinition = "TEXT")
    private String questionOrder;

    @Column(name = "needs_manual_grade", nullable = false)
    private boolean needsManualGrade;

    /** JSON [{type, at}] of focus/fullscreen/copy events reported by the exam client (§12). */
    @Column(name = "integrity_events", columnDefinition = "TEXT")
    private String integrityEvents;

    @Column(name = "fullscreen_exits", nullable = false)
    private int fullscreenExits;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
