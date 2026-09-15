package com.manarah.payment.repo;

import com.manarah.payment.domain.Installment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    List<Installment> findByTenantIdAndInvoiceIdOrderBySeq(Long tenantId, Long invoiceId);
    Optional<Installment> findByTenantIdAndId(Long tenantId, Long id);
    List<Installment> findByTenantIdAndStatusAndDueDateBefore(Long tenantId, String status, LocalDate date);
    List<Installment> findByTenantIdAndStatus(Long tenantId, String status);
}
