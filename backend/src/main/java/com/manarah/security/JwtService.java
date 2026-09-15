package com.manarah.security;

import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class JwtService {
    private static final String RETIRED_DEMO_SECRET = "c2VjdXJlLW1hbmFyYWgtZGV2LXNlY3JldC1rZXktY2hhbmdlLWluLXByb2R1Y3Rpb24tMzJieXRl";

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
        if (props.getSecret() == null || props.getSecret().isBlank()) {
            throw new IllegalStateException("MANARAH_JWT_SECRET is required");
        }
        if (RETIRED_DEMO_SECRET.equals(props.getSecret())) {
            throw new IllegalStateException("The documented demo JWT secret is forbidden; generate a unique secret");
        }
        // Accept a Base64 secret; fall back to raw bytes if not valid Base64.
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(props.getSecret());
        } catch (IllegalArgumentException e) {
            secret = props.getSecret().getBytes();
        }
        if (secret.length < 32) throw new IllegalStateException("MANARAH_JWT_SECRET must contain at least 256 bits");
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("tid", user.getTenantId())
                .claim("bid", user.getBranchId())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .claim("email", user.getEmail())
                .claim("uv", userVersion(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims c = claims(token);
        Long branchId = c.get("bid") == null ? null : ((Number) c.get("bid")).longValue();
        return new UserPrincipal(
                Long.valueOf(c.getSubject()),
                ((Number) c.get("tid")).longValue(),
                branchId,
                c.get("name", String.class),
                c.get("email", String.class),
                Role.valueOf(c.get("role", String.class)));
    }

    /** Invalidates every outstanding token immediately after a password change. */
    public boolean isCurrentFor(String token, User user) {
        Claims claims = claims(token);
        return String.valueOf(user.getId()).equals(claims.getSubject())
                && userVersion(user).equals(claims.get("uv", String.class));
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String playbackTicket(UserPrincipal actor, Long materialId) {
        return Jwts.builder().subject(String.valueOf(actor.getId())).claim("purpose", "video-playback")
                .claim("tenant", actor.getTenantId()).claim("material", materialId)
                .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
    }

    public void verifyPlaybackTicket(String ticket, UserPrincipal actor, Long materialId) {
        try {
            Claims c = claims(ticket);
            if (!"video-playback".equals(c.get("purpose")) || !String.valueOf(actor.getId()).equals(c.getSubject())
                    || !actor.getTenantId().equals(((Number)c.get("tenant")).longValue())
                    || !materialId.equals(((Number)c.get("material")).longValue())) throw new IllegalArgumentException();
        } catch (Exception e) { throw new com.manarah.common.exception.ApiExceptions.ForbiddenException("انتهى تصريح المشاهدة أو أنه غير صالح؛ أعد فتح الفيديو"); }
    }

    private String userVersion(User user) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            byte[] digest = mac.doFinal(user.getPasswordHash().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive token version", e);
        }
    }

    public long getAccessTokenTtlMinutes() {
        return props.getAccessTokenTtlMinutes();
    }
}
