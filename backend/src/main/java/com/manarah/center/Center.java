package com.manarah.center;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A tutoring center (سنتر): its own tenant, run by the CENTER_ADMIN account the head office created for it. */
@Entity @Table(name = "centers") @Getter @Setter
public class Center {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    /** The head office that created the center and may lock or re-key it. */
    private Long managerTenantId;
    private Long ownerUserId;
    private String name;
    private String slug;
    private String phone;
    private String address;
    /** A scan also records the session fee as collected — the usual "pay at the door" center. */
    private boolean autoPay = true;
    private int nextStudentCode = 1001;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
