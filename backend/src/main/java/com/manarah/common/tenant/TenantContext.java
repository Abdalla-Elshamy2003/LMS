package com.manarah.common.tenant;

/**
 * Per-request tenant discriminator, set by the JWT filter and read by services when
 * scoping queries. ThreadLocal keeps it out of every method signature while staying
 * strictly request-bound (always cleared in a finally).
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    /** Tenant id or an error if we somehow reached tenant-scoped code unauthenticated. */
    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant bound to the current request");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
