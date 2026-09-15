package com.manarah.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manarah.security.jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenTtlMinutes = 120;
    private long refreshTokenTtlDays = 30;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getAccessTokenTtlMinutes() { return accessTokenTtlMinutes; }
    public void setAccessTokenTtlMinutes(long v) { this.accessTokenTtlMinutes = v; }
    public long getRefreshTokenTtlDays() { return refreshTokenTtlDays; }
    public void setRefreshTokenTtlDays(long v) { this.refreshTokenTtlDays = v; }
}
