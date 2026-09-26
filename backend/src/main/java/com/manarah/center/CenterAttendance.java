package com.manarah.center;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A student who came to a session. Absence is the lack of a row. */
@Entity @Table(name = "center_attendance") @Getter @Setter
public class CenterAttendance extends BaseEntity {
    public static final String QR = "QR", CODE = "CODE", MANUAL = "MANUAL";

    private Long sessionId;
    private Long studentId;
    private String method;
    /** What this session costs the student. */
    private BigDecimal amount;
    private boolean paid;
    private Instant scannedAt = Instant.now();
    private Long scannedBy;
}
