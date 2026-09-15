package com.manarah.payment.repo;

import com.manarah.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByTenantIdAndInvoiceId(Long tenantId, Long invoiceId);
    List<Payment> findByTenantIdOrderByPaidAtDesc(Long tenantId);
}
