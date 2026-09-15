package com.manarah.audit;

import com.manarah.audit.domain.AuditLog;
import com.manarah.audit.repo.AuditLogRepository;
import com.manarah.common.tenant.TenantContext;
import com.manarah.common.web.PageResponse;
import com.manarah.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(UserPrincipal actor, String action, String entityType, Long entityId,
                       String oldValue, String newValue) {
        AuditLog log = new AuditLog();
        log.setTenantId(actor != null ? actor.getTenantId() : TenantContext.require());
        log.setActorUserId(actor != null ? actor.getId() : null);
        log.setActorName(actor != null ? actor.getFullName() : "SYSTEM");
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        repo.save(log);
    }

    public PageResponse<AuditLog> list(int page, int size) {
        return PageResponse.of(
                repo.findByTenantIdOrderByCreatedAtDesc(TenantContext.require(), PageRequest.of(page, size)),
                a -> a);
    }
}
