package com.manarah.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An enrollment a plan opened, so the plan running out closes exactly those. */
@Entity @Table(name = "plan_enrollments") @Getter @Setter
public class PlanEnrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long planId;
    private Long studentId;
    private Long enrollmentId;
}
