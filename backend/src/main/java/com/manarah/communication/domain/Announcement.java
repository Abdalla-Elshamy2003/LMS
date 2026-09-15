package com.manarah.communication.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A broadcast announcement to an audience (§33). */
@Entity
@Table(name = "announcements")
@Getter
@Setter
public class Announcement extends BaseEntity {

    @Column(name = "branch_id")
    private Long branchId;

    /** ALL, STUDENTS, TEACHERS, PARENTS, GRADE */
    @Column(nullable = false)
    private String audience = "ALL";

    @Column(name = "audience_filter")
    private String audienceFilter;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
