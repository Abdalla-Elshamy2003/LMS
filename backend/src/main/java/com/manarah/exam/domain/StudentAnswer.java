package com.manarah.exam.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A student's answer to a single question within an attempt. */
@Entity
@Table(name = "student_answers")
@Getter
@Setter
public class StudentAnswer extends BaseEntity {

    @Column(name = "student_exam_id", nullable = false)
    private Long studentExamId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    /** JSON array of selected option ids. */
    @Column(name = "selected_options", columnDefinition = "TEXT")
    private String selectedOptions;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "awarded_points", nullable = false)
    private double awardedPoints;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_by")
    private Long gradedBy;
}
