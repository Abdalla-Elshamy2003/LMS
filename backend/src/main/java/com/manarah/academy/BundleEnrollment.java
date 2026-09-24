package com.manarah.academy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An enrollment a package subscription opened — so cancelling the package closes exactly those. */
@Entity @Table(name = "bundle_enrollments") @Getter @Setter
public class BundleEnrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long subscriptionId;
    private Long enrollmentId;
}
