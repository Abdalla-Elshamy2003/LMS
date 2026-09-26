package com.manarah.center;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A book or set of notes (ملزمة) the center sells, and the day it comes out. Students reserve it from their card's page. */
@Entity @Table(name = "center_books") @Getter @Setter
public class CenterBook extends BaseEntity {
    /** Null: offered to every student of the center, not one teacher's. */
    private Long teacherId;
    private String title;
    private String description;
    private String grade;
    private BigDecimal price = BigDecimal.ZERO;
    private LocalDate releaseDate;
    /** Null: no limit. */
    private Integer stock;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
