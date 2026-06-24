package com.example.panstwamiasta.config;

import com.example.panstwamiasta.auth.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSecretsGuardTest {

    private static final String GOOD_SECRET = "a-very-strong-production-secret-with-enough-length";
    private static final String GOOD_PASSWORD = "s3cure-db-pass";

    private ProductionSecretsGuard guard(String jwtSecret, String dbPassword) {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret(jwtSecret);
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.password", dbPassword);
        return new ProductionSecretsGuard(jwt, env);
    }

    @Test
    void shouldReject_defaultDevJwtSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard(JwtProperties.DEFAULT_DEV_SECRET, GOOD_PASSWORD).afterPropertiesSet());
        assertTrue(ex.getMessage().contains("app.jwt.secret"));
    }

    @Test
    void shouldReject_blankDbPassword() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard(GOOD_SECRET, "").afterPropertiesSet());
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
    }

    @Test
    void shouldReject_knownDefaultDbPassword() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard(GOOD_SECRET, "pmpass").afterPropertiesSet());
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
    }

    @Test
    void shouldPass_withStrongSecretsAndPassword() {
        assertDoesNotThrow(() -> guard(GOOD_SECRET, GOOD_PASSWORD).afterPropertiesSet());
    }

    @Test
    void shouldReportAllViolationsAtOnce() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard(JwtProperties.DEFAULT_DEV_SECRET, "postgres").afterPropertiesSet());
        assertTrue(ex.getMessage().contains("app.jwt.secret"));
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
    }
}
