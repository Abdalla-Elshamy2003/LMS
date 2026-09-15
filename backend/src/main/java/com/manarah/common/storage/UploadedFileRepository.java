package com.manarah.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    Optional<UploadedFile> findByTenantIdAndFileKey(Long tenantId, String fileKey);
}
