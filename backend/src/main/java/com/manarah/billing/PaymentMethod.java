package com.manarah.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One way to pay the platform (Vodafone Cash, InstaPay, Fawry), set up by head office. */
@Entity @Table(name = "payment_methods") @Getter @Setter
public class PaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String code;
    private String name;
    private boolean enabled;
    private String account = "";
    private String accountName = "";
    private String instructions = "";
    private int sortOrder;
    private Instant updatedAt = Instant.now();
    /** Added on top of the price, in percent (Vodafone Cash: 10 → 100 becomes 110). */
    private java.math.BigDecimal feePercent = java.math.BigDecimal.ZERO;
    /** Where manual payers send the screenshot of their transfer (WhatsApp); blank = the account number. */
    private String whatsapp = "";
}
