package com.manarah.common.storage;

import org.springframework.web.multipart.MultipartFile;

/** Storage port. Files (videos/PDFs) never live in SQLite — this abstracts where they do. */
public interface FileStorage {

    /** Store a file for a tenant and return an opaque key used to retrieve it later. */
    String store(Long tenantId, String folder, MultipartFile file);

    /** Resolve a stored key to an absolute filesystem path (local adapter) or URL. */
    java.nio.file.Path resolve(String key);
}
