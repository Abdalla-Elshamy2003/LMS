package com.manarah.security.auth;

import com.manarah.common.exception.ApiExceptions.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Brute-force login guard keyed by normalized login identifier, backed by Redis so the limit is
 * shared across every backend instance - a plain in-process map (the previous implementation)
 * would let an attacker reset their failure count just by hitting a different instance.
 *
 * <p>Fails open (logs a warning and lets the request through) if Redis is unreachable: a brief
 * Redis outage should degrade rate-limiting, not take down login entirely.
 */
@Component
public class LoginAttemptLimiter {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
    private static final String KEY_PREFIX = "login:attempts:";

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(StringRedisTemplate redis,
                               @Value("${manarah.security.login.max-failures:8}") int maxFailures,
                               @Value("${manarah.security.login.window-minutes:15}") long windowMinutes) {
        this.redis = redis;
        this.maxFailures = Math.max(3, maxFailures);
        this.window = Duration.ofMinutes(Math.max(1, windowMinutes));
    }

    public void assertAllowed(String identifier) {
        int failures;
        try {
            String raw = redis.opsForValue().get(key(identifier));
            failures = raw == null ? 0 : Integer.parseInt(raw);
        } catch (DataAccessException e) {
            log.warn("Login rate limiter unavailable (Redis), failing open: {}", e.getMessage());
            return;
        }
        if (failures >= maxFailures) {
            throw new TooManyRequestsException("محاولات دخول كثيرة. حاول مرة أخرى بعد قليل");
        }
    }

    public void failed(String identifier) {
        String key = key(identifier);
        try {
            Long failures = redis.opsForValue().increment(key);
            if (failures != null && failures == 1L) {
                // First failure in a fresh window - start the TTL now. A concurrent second failure
                // between the increment and this call still expires correctly since both requests
                // race to set the same TTL on the same key.
                redis.expire(key, window);
            }
        } catch (DataAccessException e) {
            log.warn("Login rate limiter unavailable (Redis), not recording failure: {}", e.getMessage());
        }
    }

    public void succeeded(String identifier) {
        try {
            redis.delete(key(identifier));
        } catch (DataAccessException e) {
            log.warn("Login rate limiter unavailable (Redis), not clearing counter: {}", e.getMessage());
        }
    }

    private static String key(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return KEY_PREFIX + normalized;
    }
}
