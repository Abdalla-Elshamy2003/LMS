package com.manarah.student.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A student profile (§2). Status and academic status plus the derived metrics
 * (avg score, attendance rate, homework rate, overall %) are materialised here and
 * recomputed by the metrics engine on domain events.
 */
@Entity
@Table(name = "students")
@Getter
@Setter
public class Student extends BaseEntity {

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "user_id")
    private Long userId;

    /** Stable secret behind the student's personal entry/exit QR. Generated on first use, not at
     *  signup, so existing students get one the first time they open their profile. */
    @Column(name = "pass_token")
    private String passToken;

    /** UID of a physical RFID/NFC card bound to this student, uppercased. Null until staff bind one;
     *  a printed QR/barcode card needs nothing here because it carries {@link #passToken} itself. */
    @Column(name = "card_uid")
    private String cardUid;

    @Column(nullable = false)
    private String code;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "national_id")
    private String nationalId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String gender;

    @Column(name = "grade_level")
    private String gradeLevel;

    private String grade;

    /** نظام التعليم: عادي، لغات، تجريبي */
    @Column(name = "education_type", nullable = false)
    private String educationType = "عادي";

    private String school;
    private String phone;

    /** ACTIVE, INACTIVE, SUSPENDED, GRADUATED, DROPPED, TRIAL, PENDING_PAYMENT */
    @Column(nullable = false)
    private String status = "ACTIVE";

    /** EXCELLENT, GOOD, AVERAGE, NEEDS_ATTENTION, AT_RISK (materialised) */
    @Column(name = "academic_status", nullable = false)
    private String academicStatus = "AVERAGE";

    @Column(name = "avg_score", nullable = false)
    private double avgScore;

    @Column(name = "attendance_rate", nullable = false)
    private double attendanceRate;

    @Column(name = "homework_rate", nullable = false)
    private double homeworkRate;

    @Column(name = "overall_percent", nullable = false)
    private double overallPercent;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
