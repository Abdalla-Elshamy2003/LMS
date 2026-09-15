package com.manarah.homework.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One attachment of a homework submission — a submission may carry several (photos of pages, PDF, ZIP). */
@Entity
@Table(name = "submission_files")
@Getter
@Setter
public class SubmissionFile extends BaseEntity {

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "file_key", nullable = false, unique = true)
    private String fileKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();
}
