package com.manarah.course.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** A course / subject offering (§6). */
@Entity
@Table(name = "courses")
@Getter
@Setter
public class Course extends BaseEntity {

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(nullable = false)
    private String title;

    private String subject;

    @Column(name = "grade_level")
    private String gradeLevel;

    /** The specific academic year this group targets, e.g. "الصف الأول الثانوي" — distinct from
     *  the broader gradeLevel (stage: ابتدائي/إعدادي/ثانوي). Drives the year filter on exams/homework. */
    private String grade;

    /** Free-text recurring schedule for this group, e.g. "الأحد والثلاثاء 6:00م". */
    private String schedule;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** A promotional discount set from the marketing campaigns page. Null/0 = no active discount. */
    @Column(name = "discount_percent")
    private Integer discountPercent;

    /** The price actually charged — the single source of truth used everywhere (course
     *  listings, public pages, checkout), so a displayed discount can never drift from what a
     *  student is actually billed. */
    public BigDecimal getFinalPrice() {
        if (discountPercent == null || discountPercent <= 0) return price;
        BigDecimal factor = BigDecimal.valueOf(100 - discountPercent).divide(BigDecimal.valueOf(100));
        return price.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
