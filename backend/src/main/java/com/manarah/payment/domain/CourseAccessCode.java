package com.manarah.payment.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-time code that unlocks a specific paid course, handed to the student after the teacher
 * confirms a manual transfer (InstaPay / Vodafone Cash) received outside the system. No payment
 * gateway involved — the teacher's own confirmation IS the payment record.
 */
@Entity
@Table(name = "course_access_codes")
@Getter
@Setter
public class CourseAccessCode extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, unique = true)
    private String code;

    /** UNUSED, USED, REVOKED */
    @Column(nullable = false)
    private String status = "UNUSED";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "used_by_student_id")
    private Long usedByStudentId;

    @Column(name = "used_at")
    private Instant usedAt;
}
