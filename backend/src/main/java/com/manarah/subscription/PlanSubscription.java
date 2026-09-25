package com.manarah.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One period of a student's subscription to a plan — or a request for one that waits for payment. */
@Entity @Table(name = "plan_subscriptions") @Getter @Setter
public class PlanSubscription {
    public static final String PENDING = "PENDING", ACTIVE = "ACTIVE", ENDED = "ENDED", CANCELLED = "CANCELLED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long planId;
    private Long studentId;
    private String status = PENDING;
    private int months;
    /** What this period cost, fixed when it started. */
    private BigDecimal price;
    private Instant startsAt;
    private Instant endsAt;
    /** REQUEST, CODE, TEACHER */
    private String source;
    private Long codeId;
    private Instant requestedAt = Instant.now();
    private Instant activatedAt;
    private Long activatedBy;
    private Instant remindedAt;
    private Instant endedAt;
}
