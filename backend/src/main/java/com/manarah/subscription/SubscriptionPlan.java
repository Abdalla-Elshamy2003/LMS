package com.manarah.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** What a teacher sells for one school year and subject: a price, an optional discount, and how many months it lasts. */
@Entity @Table(name = "subscription_plans") @Getter @Setter
public class SubscriptionPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    /** {@link com.manarah.common.SchoolYears#key}; the label is how the teacher's courses spell it. */
    private String yearKey;
    private String yearLabel;
    private String subjectKey;
    private String subject;
    /** Null until the teacher sets it: never read as "free". */
    private BigDecimal price;
    private int discountPercent;
    private int months = 2;
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /** The price after the teacher's discount, or null while no price is set. */
    public BigDecimal finalPrice() {
        if (price == null) return null;
        if (discountPercent <= 0) return price;
        return price.multiply(BigDecimal.valueOf(100 - discountPercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
