package com.example.panstwamiasta.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "change-me-in-dev-only-must-be-at-least-32-chars";
    private int expiryHours = 24;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getExpiryHours() {
        return expiryHours;
    }

    public void setExpiryHours(int expiryHours) {
        this.expiryHours = expiryHours;
    }
}
