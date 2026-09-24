package com.manarah.academy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A one-time code for a whole package, handed to a student once head office confirms their transfer. */
@Entity @Table(name = "bundle_access_codes") @Getter @Setter
public class BundleAccessCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long bundleId;
    private String code;
    /** UNUSED, USED, REVOKED */
    private String status = "UNUSED";
    private Long createdBy;
    private Instant createdAt = Instant.now();
    /** The account the student signs in with. */
    private Long usedByUserId;
    private Instant usedAt;
}
