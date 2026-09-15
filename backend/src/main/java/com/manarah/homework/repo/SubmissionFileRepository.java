package com.manarah.homework.repo;

import com.manarah.homework.domain.SubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Long> {
    List<SubmissionFile> findByTenantIdAndSubmissionIdOrderByUploadedAt(Long tenantId, Long submissionId);
    Optional<SubmissionFile> findByTenantIdAndFileKey(Long tenantId, String fileKey);
    void deleteByTenantIdAndSubmissionId(Long tenantId, Long submissionId);
}
