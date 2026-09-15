package com.manarah.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Issues the browser-only cookie used by video, image and download elements. It contains the
 * signed JWT but is HttpOnly, SameSite=Strict and visible only under /api/files, so the token is
 * never exposed in a URL, browser history, referrer header, or application JavaScript.
 */
@Component
public class FileSessionCookie {
    public static final String COOKIE_NAME = "manarah_file_session";

    private final boolean secure;
    private final Duration ttl;

    public FileSessionCookie(@Value("${manarah.security.cookies.secure:false}") boolean secure,
                             JwtProperties jwtProperties) {
        this.secure = secure;
        this.ttl = Duration.ofMinutes(jwtProperties.getAccessTokenTtlMinutes());
    }

    public String issue(String token) {
        return cookie(token, ttl).toString();
    }

    public String clear() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/files")
                .maxAge(maxAge)
                .build();
    }
}
