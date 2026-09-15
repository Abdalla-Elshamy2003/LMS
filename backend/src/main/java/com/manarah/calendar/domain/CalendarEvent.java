package com.manarah.calendar.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A calendar/schedule event (§21). */
@Entity
@Table(name = "calendar_events")
@Getter
@Setter
public class CalendarEvent extends BaseEntity {

    @Column(name = "branch_id")
    private Long branchId;

    /** LECTURE, EXAM, DEADLINE, EVENT, HOLIDAY, MEETING */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "related_type")
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
