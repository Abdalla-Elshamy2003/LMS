package com.manarah.enrollment.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A student's enrollment in a course (§24). */
@Entity
@Table(name = "enrollments")
@Getter
@Setter
public class Enrollment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt = Instant.now();
}
