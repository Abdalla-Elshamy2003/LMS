package com.manarah.payment.repo;

import com.manarah.payment.domain.CoursePurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CoursePurchaseOrderRepository extends JpaRepository<CoursePurchaseOrder, Long> {
    Optional<CoursePurchaseOrder> findByTenantIdAndReference(Long tenantId, String reference);
    Optional<CoursePurchaseOrder> findFirstByTenantIdAndUserIdAndCourseIdAndStatusOrderByCreatedAtDesc(Long tenantId, Long userId, Long courseId, String status);
    /** Tenant-less lookup for the Paymob webhook, which arrives with no JWT/tenant context —
     *  the order's own tenantId is then seeded into {@link com.manarah.common.tenant.TenantContext}. */
    Optional<CoursePurchaseOrder> findByReference(String reference);
}
