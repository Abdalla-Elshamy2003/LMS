package com.manarah.payment.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A recorded payment against an invoice / installment (§25). */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "installment_id")
    private Long installmentId;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    /** CASH, CARD, ONLINE, TRANSFER */
    @Column(nullable = false)
    private String method = "CASH";

    private String reference;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt = Instant.now();
}
