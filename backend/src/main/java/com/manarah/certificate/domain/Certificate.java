package com.manarah.certificate.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A course-completion certificate with a QR verification code (§28). */
@Entity
@Table(name = "certificates")
@Getter
@Setter
public class Certificate extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id")
    private Long courseId;

    private String grade;

    @Column(nullable = false)
    private String serial;

    @Column(name = "verify_code", nullable = false)
    private String verifyCode;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();
}
