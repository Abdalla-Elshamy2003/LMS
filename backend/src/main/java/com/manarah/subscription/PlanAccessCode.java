package com.manarah.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A one-time code the teacher hands a student who paid: it starts one period of the plan. */
@Entity @Table(name = "plan_access_codes") @Getter @Setter
public class PlanAccessCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long planId;
    private String code;
    /** UNUSED, USED, REVOKED */
    private String status = "UNUSED";
    private Long createdBy;
    private Instant createdAt = Instant.now();
    private Long usedByStudentId;
    private Instant usedAt;
}
