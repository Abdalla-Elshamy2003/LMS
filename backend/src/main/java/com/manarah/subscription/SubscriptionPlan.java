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

    /** Other lengths the teacher sells, each at its own price, as JSON: [{"months": 6, "price": 500}, ...]. */
    @Column(columnDefinition = "TEXT")
    private String extraOptionsJson = "[]";

    /** The price after the teacher's discount, or null while no price is set. */
    public BigDecimal finalPrice() {
        if (price == null) return null;
        if (discountPercent <= 0) return price;
        return price.multiply(BigDecimal.valueOf(100 - discountPercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** One length a student can buy, at its final price. */
    public record Option(int months, BigDecimal price) {}

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** The teacher's extra lengths, as stored (no base option, no checks). */
    public java.util.List<Option> extraOptions() {
        try {
            return java.util.Arrays.asList(JSON.readValue(extraOptionsJson == null || extraOptionsJson.isBlank() ? "[]" : extraOptionsJson, Option[].class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored plan options", e);
        }
    }

    public void setExtraOptions(java.util.List<Option> options) {
        try { extraOptionsJson = JSON.writeValueAsString(options); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    /**
     * Every length a student can choose, shortest first: the plan's own months at its final price, then the teacher's
     * other lengths. Empty while the plan has no price.
     */
    public java.util.List<Option> options() {
        if (finalPrice() == null) return java.util.List.of();
        java.util.Map<Integer, Option> byMonths = new java.util.TreeMap<>();
        byMonths.put(months, new Option(months, finalPrice()));
        for (Option o : extraOptions())
            if (o != null && o.months() > 0 && o.price() != null && o.price().signum() > 0) byMonths.putIfAbsent(o.months(), o);
        return java.util.List.copyOf(byMonths.values());
    }

    /** The choice for {@code wanted} months, or the plan's own length when none is asked for. */
    public java.util.Optional<Option> option(Integer wanted) {
        int m = wanted == null ? months : wanted;
        return options().stream().filter(o -> o.months() == m).findFirst();
    }
}
