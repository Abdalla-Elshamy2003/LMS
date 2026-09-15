package com.manarah.course.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A material attached to a lesson: video, PDF, slides, link, etc. (§7). */
@Entity
@Table(name = "lesson_materials")
@Getter
@Setter
public class LessonMaterial extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    /** VIDEO, PDF, PPT, DOC, IMAGE, AUDIO, LINK, ASSIGNMENT */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String url;

    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
