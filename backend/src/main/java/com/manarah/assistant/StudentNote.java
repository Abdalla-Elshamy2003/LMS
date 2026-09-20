package com.manarah.assistant;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** A private follow-up note about a student. Visible to the teacher and their assistants only - never to the student or parent. */
@Entity
@Table(name = "student_notes")
@Getter
@Setter
public class StudentNote extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /** CALL_PARENT, ABSENCE, ACADEMIC, BEHAVIOR, GENERAL */
    @Column(nullable = false)
    private String kind = "GENERAL";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "follow_up_on")
    private LocalDate followUpOn;

    /** OPEN, RESOLVED */
    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
