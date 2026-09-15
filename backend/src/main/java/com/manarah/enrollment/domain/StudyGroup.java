package com.manarah.enrollment.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A study group / class section of a course (§24). */
@Entity
@Table(name = "study_groups")
@Getter
@Setter
public class StudyGroup extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(nullable = false)
    private String name;

    @Column(name = "schedule_text")
    private String scheduleText;

    @Column(nullable = false)
    private int capacity = 30;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
