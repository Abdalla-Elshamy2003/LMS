package com.manarah.calendar.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "schedule_slots")
@Getter @Setter
public class ScheduleSlot extends BaseEntity {
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(name = "group_id") private Long groupId;
    @Column(name = "teacher_id") private Long teacherId;
    @Column(name = "room_id") private Long roomId;
    @Column(nullable = false) private String title;
    @Column(name = "day_of_week", nullable = false) private int dayOfWeek;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Column(name = "delivery_mode", nullable = false) private String deliveryMode = "IN_PERSON";
    @Column(name = "meeting_url") private String meetingUrl;
    @Column(nullable = false) private String color = "#0f766e";
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
