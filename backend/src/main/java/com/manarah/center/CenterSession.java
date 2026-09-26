package com.manarah.center;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A day a group actually met, opened by the first scan (or the first manual mark). */
@Entity @Table(name = "center_sessions") @Getter @Setter
public class CenterSession extends BaseEntity {
    private Long groupId;
    /** yyyy-MM-dd in Cairo, kept as text so date ranges compare as text in SQL. */
    private String sessionDate;
    /** The group's fee the day it met, so a later price change never rewrites the past. */
    private BigDecimal price;
    private Instant createdAt = Instant.now();
}
