package com.manarah.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Storage port. Files (videos/PDFs) never live in the database - this abstracts where they do.
 * {@code LocalFileStorage} (disk) is only safe with exactly one backend instance; {@code
 * S3FileStorage} is required once more than one instance runs, since a file written to one
 * instance's disk is invisible to the others. Selected via manarah.storage.provider.
 */
public interface FileStorage {

    /** Store a file for a tenant and return an opaque key used to retrieve it later. */
    String store(Long tenantId, String folder, MultipartFile file);

    /**
     * Resolve a stored key to a logical path used only for tenant-ownership and path-traversal
     * checks (callers compare this against a tenant-root path with {@code startsWith}). This is
     * pure in-memory path arithmetic - it never touches disk, so it is safe to implement the same
     * way regardless of where the bytes actually live.
     */
    java.nio.file.Path resolve(String key);

    /** Open a stored key for reading. Returns a Resource whose {@code exists()} is false if the key is unknown. */
    Resource open(String key);
}
