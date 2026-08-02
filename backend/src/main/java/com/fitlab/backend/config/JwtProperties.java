package com.fitlab.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** fitlab.jwt.secret signs every auth token; override via JWT_SECRET in production. */
@ConfigurationProperties(prefix = "fitlab.jwt")
public class JwtProperties {

    private String secret = "changeme-dev-only-jwt-secret-please-override-in-production-0123456789";

    private long expirationMinutes = 43200; // 30 days

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
