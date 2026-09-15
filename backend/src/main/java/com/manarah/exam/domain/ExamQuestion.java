package com.manarah.exam.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Join of an exam to a bank question, with position and optional points override. */
@Entity
@Table(name = "exam_questions")
@Getter
@Setter
public class ExamQuestion extends BaseEntity {

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private int position;

    @Column(name = "points_override")
    private Double pointsOverride;
}
