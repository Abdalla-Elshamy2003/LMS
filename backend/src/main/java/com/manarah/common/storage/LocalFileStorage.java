package com.manarah.common.storage;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Filesystem-backed {@link FileStorage}. Only safe with exactly one backend instance - see
 * {@link S3FileStorage} for the multi-instance-safe adapter. Selected by default
 * (manarah.storage.provider=local, or unset) for local development.
 */
@Component
@ConditionalOnProperty(name = "manarah.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${manarah.storage.root:./data/files}") String root) {
        this.root = Path.of(root);
    }

    @Override
    public String store(Long tenantId, String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("الملف فارغ");
        }
        if (folder == null || !folder.matches("[a-zA-Z0-9_-]+")) {
            throw new BadRequestException("مسار رفع غير صالح");
        }
        try {
            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
            String key = "t" + tenantId + "/" + folder + "/" + UUID.randomUUID() + "_" + safe;
            Path target = root.resolve(key);
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
            return key;
        } catch (IOException e) {
            throw new BadRequestException("تعذّر حفظ الملف: " + e.getMessage());
        }
    }

    @Override
    public Path resolve(String key) {
        return root.resolve(key);
    }

    @Override
    public Resource open(String key) {
        return new FileSystemResource(resolve(key));
    }
}
