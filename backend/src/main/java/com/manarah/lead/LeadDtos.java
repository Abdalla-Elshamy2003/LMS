package com.manarah.lead;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class LeadDtos {

    public record ContactRequest(@NotBlank String fullName, @NotBlank String email, String phone,
                                 String institutionType, String expectedStudents, String message) {
    }

    public record LeadView(Long id, String fullName, String email, String phone, String institutionType,
                           String expectedStudents, String message, String status, Instant createdAt) {
    }
}
