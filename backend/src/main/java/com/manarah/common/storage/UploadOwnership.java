package com.manarah.common.storage;

import com.manarah.security.UserPrincipal;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import org.springframework.stereotype.Service;

@Service
public class UploadOwnership {
    private final UploadedFileRepository files;
    public UploadOwnership(UploadedFileRepository files) { this.files = files; }
    public void record(UserPrincipal actor, String key) {
        UploadedFile file = new UploadedFile();
        file.setTenantId(actor.getTenantId()); file.setOwnerId(actor.getId()); file.setFileKey(key);
        files.save(file);
    }
    public void requireOwned(UserPrincipal actor, String key, String existingKey) {
        if (key == null || key.isBlank() || key.equals(existingKey)) return;
        if (files.findByTenantIdAndFileKey(actor.getTenantId(), key)
                .filter(f -> f.getOwnerId().equals(actor.getId())).isEmpty())
            throw new ForbiddenException("ارفع الملف من حسابك قبل إرفاقه");
    }
}
