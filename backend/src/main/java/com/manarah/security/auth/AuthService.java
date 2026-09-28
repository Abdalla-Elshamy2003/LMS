package com.manarah.security.auth;

import com.manarah.common.exception.ApiExceptions.AccountBlockedException;
import com.manarah.common.exception.ApiExceptions.UnauthorizedException;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.JwtService;
import com.manarah.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttempts;
    private final com.manarah.academy.LinkedStudentAccounts linkedAccounts;
    private final com.manarah.identity.AccountBlocks blocks;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptLimiter loginAttempts, com.manarah.academy.LinkedStudentAccounts linkedAccounts,
                       com.manarah.identity.AccountBlocks blocks) {
        this.blocks = blocks;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
        this.linkedAccounts = linkedAccounts;
        this.dummyPasswordHash = passwordEncoder.encode("timing-only-password-value");
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        String identifier = req.email().trim();
        loginAttempts.assertAllowed(identifier);
        User user = authenticate(identifier, req.password());
        if (user == null) {
            loginAttempts.failed(identifier);
            throw new UnauthorizedException("بيانات الدخول غير صحيحة");
        }
        loginAttempts.succeeded(identifier);
        // The password was right, so this is the person themselves: they are told why they are blocked, if they are.
        String blocked = blocks.accountReason(user);
        if (blocked != null) throw new AccountBlockedException(blocked);
        // A student lands in their own teacher space — or, if that teacher removed or blocked them, in one they still
        // have. Blocked by the only teacher they have, they are told so.
        User landing = linkedAccounts.landing(user);
        blocked = blocks.reason(landing, user);
        if (blocked != null) throw new AccountBlockedException(blocked);
        user.setLastLoginAt(Instant.now());
        String token = linkedAccounts.tokenFor(landing);
        return new AuthDtos.TokenResponse(token, "Bearer",
                jwtService.getAccessTokenTtlMinutes(), toProfile(landing));
    }

    /**
     * The active account these credentials open, or null. Email is unique per teacher space only, so one address
     * may belong to several accounts: take the one whose password matches. Linked rows (a student's seat with a
     * second teacher) have placeholder emails and no usable password, so they never match here.
     */
    User authenticate(String identifier, String password) {
        List<User> candidates = identifier.contains("@")
                ? users.findAllByEmailIgnoreCase(identifier)
                : users.findByUsernameIgnoreCase(identifier).map(List::of).orElse(List.of());
        if (candidates.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash); // same work whether or not the account exists
            return null;
        }
        return candidates.stream()
                .filter(u -> u.getPrimaryUserId() == null && "ACTIVE".equals(u.getStatus()))
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .findFirst().orElse(null);
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
