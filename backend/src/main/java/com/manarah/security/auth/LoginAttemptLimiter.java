package com.manarah.security.auth;

import com.manarah.common.exception.ApiExceptions.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-process brute-force guard keyed by normalized login identifier. */
@Component
public class LoginAttemptLimiter {
    private record Attempt(int failures, Instant windowStart) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(@Value("${manarah.security.login.max-failures:8}") int maxFailures,
                               @Value("${manarah.security.login.window-minutes:15}") long windowMinutes) {
        this.maxFailures = Math.max(3, maxFailures);
        this.window = Duration.ofMinutes(Math.max(1, windowMinutes));
    }

    public void assertAllowed(String identifier) {
        Attempt attempt = attempts.get(key(identifier));
        if (attempt != null && !expired(attempt) && attempt.failures >= maxFailures) {
            throw new TooManyRequestsException("محاولات دخول كثيرة. حاول مرة أخرى بعد قليل");
        }
    }

    public void failed(String identifier) {
        String key = key(identifier);
        attempts.compute(key, (ignored, current) -> current == null || expired(current)
                ? new Attempt(1, Instant.now()) : new Attempt(current.failures + 1, current.windowStart));
    }

    public void succeeded(String identifier) {
        attempts.remove(key(identifier));
    }

    private boolean expired(Attempt attempt) {
        return attempt.windowStart.plus(window).isBefore(Instant.now());
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
