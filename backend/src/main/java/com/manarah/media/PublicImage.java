package com.manarah.media;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A validated PNG/JPEG a teacher uploaded as a course or video-lesson cover, served publicly by id. */
@Entity
@Table(name = "public_images")
@Getter
@Setter
public class PublicImage extends BaseEntity {

    @Column(name = "content_type", nullable = false)
    private String contentType;

    /** Base64 of the raw image bytes. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String data;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
