package com.example.panstwamiasta.config;

import com.example.panstwamiasta.auth.JwtProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast guard: pod profilem "prod" odmawia startu, gdy wykryje wbudowane
 * domyślne sekrety (dev JWT, puste lub znane domyślne hasło DB). Chroni przed
 * przypadkowym wdrożeniem na publicznych defaultach.
 *
 * Hasło DB czytamy z Environment po kluczu "spring.datasource.password" — to ta
 * sama właściwość, z której Spring Boot buduje DataSource (resolved placeholder).
 */
@Component
@Profile("prod")
public class ProductionSecretsGuard implements InitializingBean {

    private final JwtProperties jwtProperties;
    private final Environment environment;

    public ProductionSecretsGuard(JwtProperties jwtProperties, Environment environment) {
        this.jwtProperties = jwtProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = new ArrayList<>();

        if (JwtProperties.DEFAULT_DEV_SECRET.equals(jwtProperties.getSecret())) {
            violations.add("app.jwt.secret uses the built-in dev default");
        }

        String dbPassword = environment.getProperty("spring.datasource.password", "");
        if (dbPassword.isBlank()
                || dbPassword.equals("postgres") || dbPassword.equals("pmpass")) {
            violations.add("spring.datasource.password is empty or a known default");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with insecure secrets in 'prod' profile: " + violations);
        }
    }
}
