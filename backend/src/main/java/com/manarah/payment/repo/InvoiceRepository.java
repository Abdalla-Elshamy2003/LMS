package com.manarah.payment.repo;

import com.manarah.payment.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByTenantId(Long tenantId);

    List<Invoice> findByTenantIdAndStudentId(Long tenantId, Long studentId);

    Optional<Invoice> findByTenantIdAndId(Long tenantId, Long id);

    List<Invoice> findByTenantIdAndStatusIn(Long tenantId, List<String> statuses);

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.tenantId = :tenantId")
    BigDecimal totalCollected(@Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(i.totalAmount - i.discount - i.paidAmount), 0) FROM Invoice i WHERE i.tenantId = :tenantId AND i.status <> 'PAID'")
    BigDecimal totalOutstanding(@Param("tenantId") Long tenantId);
}
