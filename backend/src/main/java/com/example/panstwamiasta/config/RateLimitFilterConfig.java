package com.example.panstwamiasta.config;

import com.example.panstwamiasta.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilterConfig {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.addUrlPatterns("/api/rooms", "/api/rooms/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static class RateLimitFilter extends OncePerRequestFilter {

        private final LettuceBasedProxyManager<String> proxyManager;
        private final RateLimitProperties properties;
        private final ObjectMapper objectMapper;

        RateLimitFilter(LettuceBasedProxyManager<String> proxyManager,
                        RateLimitProperties properties,
                        ObjectMapper objectMapper) {
            this.proxyManager = proxyManager;
            this.properties = properties;
            this.objectMapper = objectMapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (!"POST".equalsIgnoreCase(request.getMethod()) || !isRateLimitedPath(request.getRequestURI())) {
                filterChain.doFilter(request, response);
                return;
            }

            String clientKey = resolveClientIp(request);
            Bucket bucket = proxyManager.builder().build(clientKey, this::bucketConfiguration);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(), new ApiError("Too many requests"));
                return;
            }

            filterChain.doFilter(request, response);
        }

        private boolean isRateLimitedPath(String path) {
            if ("/api/rooms".equals(path)) {
                return true;
            }
            return path.matches("/api/rooms/[^/]+/join");
        }

        private String resolveClientIp(HttpServletRequest request) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }

        private BucketConfiguration bucketConfiguration() {
            return BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(properties.getCapacity())
                            .refillGreedy(properties.getCapacity(),
                                    Duration.ofSeconds(properties.getRefillPeriodSeconds()))
                            .build())
                    .build();
        }
    }

    @Bean
    RateLimitFilter rateLimitFilter(LettuceBasedProxyManager<String> rateLimitProxyManager,
                                    RateLimitProperties properties,
                                    ObjectMapper objectMapper) {
        return new RateLimitFilter(rateLimitProxyManager, properties, objectMapper);
    }
}
