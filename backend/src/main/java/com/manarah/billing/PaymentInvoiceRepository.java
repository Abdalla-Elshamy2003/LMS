package com.manarah.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoice, Long> {
    List<PaymentInvoice> findByStudentIdInOrderByIdDesc(Collection<Long> studentIds);
    List<PaymentInvoice> findByPlanSubscriptionIdOrderByIdDesc(Long planSubscriptionId);
    Optional<PaymentInvoice> findByGatewayInvoiceId(String gatewayInvoiceId);
    Optional<PaymentInvoice> findByGatewayInvoiceKey(String gatewayInvoiceKey);
    boolean existsByNumber(String number);

    /** The invoice, locked until the transaction ends — so a repeated webhook and an approval can't both pay it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentInvoice i where i.id = :id")
    Optional<PaymentInvoice> lockById(@Param("id") Long id);
}
