package com.manarah.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Per-IP request throttling for the handful of unauthenticated POST endpoints (see
 * SecurityConfig's permitAll list) that would otherwise have no abuse limit at all: password
 * reset requests, public self-registration, checkout, access-code redemption and the contact
 * form. Login has its own {@link com.manarah.security.auth.LoginAttemptLimiter} with different
 * (post-failure) semantics; this is a flat "N attempts per window" throttle applied before the
 * request reaches the controller.
 *
 * <p>Backed by the same Redis fixed-window-counter pattern as LoginAttemptLimiter, and fails open
 * (logs and lets the request through) if Redis is unreachable - a Redis outage should not take
 * the public site down.
 */
@Component
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(PublicEndpointRateLimitFilter.class);
    private static final String KEY_PREFIX = "ratelimit:public:";

    private record Rule(String method, String path, int maxRequests, Duration window) {}

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/forgot-password", 5, Duration.ofMinutes(15)),
            new Rule("POST", "/api/public/register", 10, Duration.ofMinutes(15)),
            new Rule("POST", "/api/public/checkout", 20, Duration.ofMinutes(15)),
            new Rule("POST", "/api/public/redeem-code", 10, Duration.ofMinutes(15)),
            new Rule("POST", "/api/public/contact", 5, Duration.ofMinutes(15))
    );

    private final StringRedisTemplate redis;

    public PublicEndpointRateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = RULES.stream()
                .filter(r -> r.method().equalsIgnoreCase(request.getMethod()) && r.path().equals(request.getRequestURI()))
                .findFirst().orElse(null);

        if (rule != null && !allow(rule, clientIp(request))) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"محاولات كثيرة. حاول مرة أخرى بعد قليل\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(Rule rule, String ip) {
        String key = KEY_PREFIX + rule.path() + ":" + ip;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, rule.window());
            }
            return count == null || count <= rule.maxRequests();
        } catch (DataAccessException e) {
            log.warn("Public endpoint rate limiter unavailable (Redis), failing open: {}", e.getMessage());
            return true;
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
