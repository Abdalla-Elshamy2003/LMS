package com.manarah.payment.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "course_purchase_orders")
@Getter @Setter
public class CoursePurchaseOrder extends BaseEntity {
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(name="student_id", nullable=false) private Long studentId;
    @Column(name="course_id", nullable=false) private Long courseId;
    @Column(nullable=false, unique=true) private String reference;
    @Column(nullable=false) private BigDecimal amount;
    @Column(nullable=false) private String currency = "EGP";
    @Column(name="payment_method") private String paymentMethod;
    @Column(nullable=false) private String status = "PENDING";
    @Column(name="provider_reference") private String providerReference;
    @Column(name="paid_at") private Instant paidAt;
    @Column(name="created_at", nullable=false) private Instant createdAt = Instant.now();
    /** Real gateway hosted-checkout URL; null when no live gateway is configured (demo/sandbox mode applies instead). */
    @Column(name="checkout_url") private String checkoutUrl;
}
