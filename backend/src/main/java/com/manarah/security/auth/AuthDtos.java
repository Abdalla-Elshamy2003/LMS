package com.manarah.security.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    public record UserProfile(
            Long id,
            String fullName,
            String email,
            String phone,
            String role,
            String roleArabic,
            Long tenantId,
            Long branchId,
            String avatarUrl) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresInMinutes,
            UserProfile user) {
    }
}
