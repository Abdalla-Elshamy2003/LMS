package com.manarah.center;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One of a teacher's groups (حصة): the subject and year, the days and hour it meets, and what one session costs. */
@Entity @Table(name = "center_groups") @Getter @Setter
public class CenterGroup extends BaseEntity {
    private Long teacherId;
    private String name;
    private String subject;
    private String grade;
    /** Comma list of days, 0 = Sunday … 6 = Saturday — see {@link CenterDays}. */
    private String days;
    /** HH:mm, Cairo time. */
    private String startTime;
    private String endTime;
    private String room;
    private BigDecimal sessionPrice = BigDecimal.ZERO;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
