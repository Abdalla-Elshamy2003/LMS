package com.manarah.center;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One student in one group — so the QR on the card says exactly which teacher, subject, day and hour it is for. The
 * same child in a second group gets a second card.
 */
@Entity @Table(name = "center_students") @Getter @Setter
public class CenterStudent extends BaseEntity {
    private Long groupId;
    /** The short number printed under the QR, typed at the desk when a card won't scan. */
    private String code;
    /** What the QR carries (inside the card's link). Replaced when the center issues a new card. */
    private String token;
    private String name;
    private String phone;
    private String parentPhone;
    private boolean active = true;
    private LocalDate joinedOn;
    private Instant createdAt = Instant.now();
}
