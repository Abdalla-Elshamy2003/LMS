package com.manarah.center;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A student's hold on a book; the code is what they show at the desk to collect it. */
@Entity @Table(name = "center_book_reservations") @Getter @Setter
public class CenterBookReservation extends BaseEntity {
    public static final String RESERVED = "RESERVED", DELIVERED = "DELIVERED", CANCELLED = "CANCELLED";
    public static final String BY_STUDENT = "STUDENT", BY_CENTER = "CENTER";

    private Long bookId;
    private Long studentId;
    private String code;
    private String status = RESERVED;
    /** The book's price when it was reserved. */
    private BigDecimal price;
    private String source;
    private Instant reservedAt = Instant.now();
    private Instant deliveredAt;
    private Instant cancelledAt;
}
