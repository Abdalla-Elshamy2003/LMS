package com.manarah.academy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A student's subscription to a whole package: every teacher in it, all of their courses. */
@Entity @Table(name = "bundle_subscriptions") @Getter @Setter
public class BundleSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long bundleId;
    /** The account the student signs in with (never a linked seat). */
    private Long userId;
    /** ACTIVE, CANCELLED */
    private String status = "ACTIVE";
    /** CODE, ADMIN */
    private String source;
    private Long codeId;
    private Long grantedBy;
    private Instant createdAt = Instant.now();
    private Instant cancelledAt;
}
