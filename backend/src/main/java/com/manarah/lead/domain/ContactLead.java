package com.manarah.lead.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A prospective customer's message from the public "تواصل معنا" contact form (pre-signup —
 *  they have no account/tenant of their own yet, so this attaches to the marketing tenant). */
@Entity
@Table(name = "contact_leads")
@Getter
@Setter
public class ContactLead extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "institution_type")
    private String institutionType;

    @Column(name = "expected_students")
    private String expectedStudents;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private String status = "NEW";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
