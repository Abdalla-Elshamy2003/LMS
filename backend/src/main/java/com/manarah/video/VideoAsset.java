package com.manarah.video;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A lesson video uploaded straight to object storage, and — once processed — its HLS rendition. */
@Entity @Table(name = "video_assets") @Getter @Setter
public class VideoAsset {
    public static final String UPLOADING = "UPLOADING", READY = "READY", QUEUED = "QUEUED", PROCESSING = "PROCESSING",
            STREAMING = "STREAMING", ABORTED = "ABORTED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long lessonId;
    private Long uploadedBy;
    private String title = "";
    /** LOCAL or R2 — where this video's files live, fixed at upload. */
    private String storage;
    private String objectKey;
    private String fileName = "";
    private String contentType;
    private long sizeBytes;
    private String uploadId;
    private long partSize;
    private int parts;
    private String status = UPLOADING;
    private String hlsPrefix;
    /** AES-128 key for the HLS segments, hex. Only ever sent to a live watch session. */
    private String hlsKey;
    private Integer durationSec;
    private String error = "";
    private Instant createdAt = Instant.now();
    private Instant completedAt;
    private Instant processedAt;
}
