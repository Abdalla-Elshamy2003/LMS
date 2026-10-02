package com.manarah.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a student owes for one subscription request, and how they're paying it: the price of the length they chose,
 * the method's fee on top, and the total — all fixed by the server when the invoice is made. A Fawry invoice also
 * carries the gateway's reference code the student pays at any Fawry outlet. See {@link InvoiceService}.
 */
@Entity @Table(name = "payment_invoices") @Getter @Setter
public class PaymentInvoice {
    public static final String UNPAID = "UNPAID";
    public static final String AWAITING_REVIEW = "AWAITING_REVIEW";
    public static final String PAID = "PAID";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String number;
    private Long tenantId;
    private Long studentId;
    private Long planSubscriptionId;
    private String description;
    private int months;
    private String methodCode;
    private BigDecimal baseAmount;
    private BigDecimal feePercent = BigDecimal.ZERO;
    private BigDecimal feeAmount = BigDecimal.ZERO;
    private BigDecimal total;
    private String status = UNPAID;
    /** Why head office sent a receipt back, shown to the student. */
    private String note = "";
    private String gateway;
    private String gatewayInvoiceId;
    private String gatewayInvoiceKey;
    private String fawryCode;
    private Instant expiresAt;
    private Instant paidAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /** Still waiting for the student's money (a sent receipt waits for head office instead). */
    public boolean open() {
        return UNPAID.equals(status) || AWAITING_REVIEW.equals(status);
    }
}
