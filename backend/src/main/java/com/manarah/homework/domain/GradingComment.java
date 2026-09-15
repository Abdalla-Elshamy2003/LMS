package com.manarah.homework.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A teacher's reusable feedback snippet (Google Classroom "comment bank", Canvas "comment library"). */
@Entity
@Table(name = "grading_comments")
@Getter
@Setter
public class GradingComment extends BaseEntity {

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false)
    private int uses;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
