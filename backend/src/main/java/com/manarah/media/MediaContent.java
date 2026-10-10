package com.manarah.media;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

/** Keeps new image bytes in object storage while reading existing database images unchanged. */
@Service
public class MediaContent {
    private static final String PREFIX = "object:";
    private final FileStorage storage;
    private final boolean objectStorage;

    public MediaContent(FileStorage storage, @Value("${manarah.storage.provider:local}") String provider) {
        this.storage = storage;
        this.objectStorage = "s3".equals(provider);
    }

    /** The caller validates the image and authorizes its tenant before this writes anything. */
    public String storeImage(Long tenantId, String folder, MultipartFile file, String type, boolean dataUri) {
        if (!Set.of("image/png", "image/jpeg").contains(type)) throw new BadRequestException("نوع صورة غير صالح");
        if (objectStorage) return PREFIX + type + ":" + storage.store(tenantId, folder, file);
        try {
            String encoded = Base64.getEncoder().encodeToString(file.getBytes());
            return dataUri ? "data:" + type + ";base64," + encoded : encoded;
        } catch (IOException e) { throw new BadRequestException("تعذّر قراءة الصورة"); }
    }

    public byte[] read(String stored) {
        if (stored != null && stored.startsWith(PREFIX)) {
            String[] parts = reference(stored);
            var resource = storage.open(parts[2]);
            if (!resource.exists()) throw new NotFoundException("الملف غير متاح");
            try (var input = resource.getInputStream()) {
                byte[] bytes = input.readNBytes((int) ImageFiles.MAX_BYTES + 1);
                if (bytes.length > ImageFiles.MAX_BYTES) throw new IllegalStateException("Stored image exceeds its upload limit");
                return bytes;
            } catch (IOException e) { throw new IllegalStateException("Unable to read stored image", e); }
        }
        if (stored == null) throw new NotFoundException("الملف غير متاح");
        String encoded = stored.startsWith("data:") ? stored.substring(stored.indexOf(',') + 1) : stored;
        return Base64.getDecoder().decode(encoded);
    }

    public String contentType(String stored) {
        return stored.startsWith(PREFIX) ? reference(stored)[1] : stored.substring(5, stored.indexOf(';'));
    }

    /** Authenticated editor previews also work for unpublished teachers. Never saves the preview in the database. */
    public String preview(String stored) {
        if (stored == null) return "";
        return stored.startsWith(PREFIX) ? "data:" + contentType(stored) + ";base64," + Base64.getEncoder().encodeToString(read(stored)) : stored;
    }

    private static String[] reference(String stored) {
        String[] parts = stored.split(":", 3);
        if (parts.length != 3 || !Set.of("image/png", "image/jpeg").contains(parts[1])
                || !parts[2].matches("t[0-9]+/[a-zA-Z0-9_-]+/[a-zA-Z0-9._-]+"))
            throw new IllegalStateException("Invalid stored image reference");
        return parts;
    }
}
