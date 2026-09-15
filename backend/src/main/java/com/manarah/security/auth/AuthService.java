package com.manarah.security.auth;

import com.manarah.common.exception.ApiExceptions.UnauthorizedException;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.JwtService;
import com.manarah.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttempts;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptLimiter loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
        this.dummyPasswordHash = passwordEncoder.encode("timing-only-password-value");
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        String identifier = req.email().trim();
        loginAttempts.assertAllowed(identifier);
        Optional<User> found = identifier.contains("@")
                ? users.findByEmailIgnoreCase(identifier) : users.findByUsernameIgnoreCase(identifier);
        User user = found.orElse(null);
        boolean passwordMatches = passwordEncoder.matches(req.password(), user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !passwordMatches) {
            loginAttempts.failed(identifier);
            throw new UnauthorizedException("بيانات الدخول غير صحيحة");
        }
        loginAttempts.succeeded(identifier);
        user.setLastLoginAt(Instant.now());
        String token = jwtService.generateAccessToken(user);
        return new AuthDtos.TokenResponse(token, "Bearer",
                jwtService.getAccessTokenTtlMinutes(), toProfile(user));
    }

    public AuthDtos.UserProfile me(UserPrincipal principal) {
        User user = users.findByTenantIdAndId(principal.getTenantId(), principal.getId())
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new UnauthorizedException("الجلسة غير صالحة"));
        return toProfile(user);
    }

    private AuthDtos.UserProfile toProfile(User u) {
        return new AuthDtos.UserProfile(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRole().name(), u.getRole().getArabicName(), u.getTenantId(), u.getBranchId(), u.getAvatarUrl());
    }
}
