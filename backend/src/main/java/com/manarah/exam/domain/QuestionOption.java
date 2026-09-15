package com.manarah.exam.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An answer option for MCQ / MULTI_SELECT / TRUE_FALSE questions. */
@Entity
@Table(name = "question_options")
@Getter
@Setter
public class QuestionOption extends BaseEntity {

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private int position;
}
