package com.manarah.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A payment a student sent for a subscription request, waiting for head office to check it. */
@Entity @Table(name = "payment_submissions") @Getter @Setter
public class PaymentSubmission {
    public static final String SUBMITTED = "SUBMITTED", APPROVED = "APPROVED", REJECTED = "REJECTED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long studentId;
    private Long planSubscriptionId;
    private String methodCode;
    private BigDecimal amount;
    private String reference = "";
    private String sender = "";
    private String receiptType;
    /** Base64 image; only head office and the student who sent it can open it. */
    @Basic(fetch = FetchType.LAZY) @Column(columnDefinition = "TEXT") private String receiptData;
    private String status = SUBMITTED;
    private String note = "";
    private Long reviewedBy;
    private Instant reviewedAt;
    private Instant createdAt = Instant.now();
}
