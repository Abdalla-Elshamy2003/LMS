package com.manarah.center;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A teacher who teaches at the center. A record the center keeps, not a login. */
@Entity @Table(name = "center_teachers") @Getter @Setter
public class CenterTeacher extends BaseEntity {
    private String name;
    private String subject;
    private String phone;
    /** The center's cut, in percent, of what this teacher's sessions collect. */
    private int centerPercent;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
