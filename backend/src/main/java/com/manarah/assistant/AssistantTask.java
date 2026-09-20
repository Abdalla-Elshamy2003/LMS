package com.manarah.assistant;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** A piece of work the teacher hands to their assistant (chase a payment, call a parent, prepare a sheet...). */
@Entity
@Table(name = "assistant_tasks")
@Getter
@Setter
public class AssistantTask extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String details;

    /** LOW, NORMAL, HIGH */
    @Column(nullable = false)
    private String priority = "NORMAL";

    /** TODO, DOING, DONE */
    @Column(nullable = false)
    private String status = "TODO";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "course_id")
    private Long courseId;

    /** Null means any assistant of this teacher may pick it up. */
    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "result_note", columnDefinition = "TEXT")
    private String resultNote;
}
