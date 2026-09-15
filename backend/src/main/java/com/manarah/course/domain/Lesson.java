package com.manarah.course.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A lesson inside a module (§6). */
@Entity
@Table(name = "lessons")
@Getter
@Setter
public class Lesson extends BaseEntity {

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Null = visible to enrolled students immediately; a future instant hides it from
     *  students/parents until then. Staff always see it regardless, for authoring/preview. */
    @Column(name = "release_at")
    private Instant releaseAt;

    /** AI-written recap the teacher generated for this lesson; shown to students under the video. */
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;
}
