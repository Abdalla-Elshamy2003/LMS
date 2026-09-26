package com.manarah.center;

import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Component;

/** The one center a CENTER_ADMIN runs — their own tenant, so everything below is fenced to it. */
@Component
public class CenterScope {
    private final CenterRepository centers;

    public CenterScope(CenterRepository centers) { this.centers = centers; }

    public Center require(UserPrincipal actor) {
        if (actor == null || actor.getRole() != Role.CENTER_ADMIN) throw new ForbiddenException("الصفحة دي لإدارة السنتر بس");
        return centers.findByTenantId(actor.getTenantId())
                .orElseThrow(() -> new ForbiddenException("الحساب ده مش مربوط بسنتر"));
    }

    /** {@link #require}, with the row locked — used where the center's student-code counter is taken. */
    public Center lock(UserPrincipal actor) {
        if (actor == null || actor.getRole() != Role.CENTER_ADMIN) throw new ForbiddenException("الصفحة دي لإدارة السنتر بس");
        return centers.lockByTenantId(actor.getTenantId())
                .orElseThrow(() -> new ForbiddenException("الحساب ده مش مربوط بسنتر"));
    }

    public boolean isCenterTenant(Long tenantId) { return tenantId != null && centers.findByTenantId(tenantId).isPresent(); }
}
