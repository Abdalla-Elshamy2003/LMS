package com.manarah.payment.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One installment of an invoice (§26). */
@Entity
@Table(name = "installments")
@Getter
@Setter
public class Installment extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** PENDING, PAID, OVERDUE */
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "paid_at")
    private Instant paidAt;
}
