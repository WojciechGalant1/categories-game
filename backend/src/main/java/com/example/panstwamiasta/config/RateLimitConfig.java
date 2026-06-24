package com.example.panstwamiasta.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.sentinel.master:}")
    private String sentinelMaster;

    @Value("${spring.data.redis.sentinel.nodes:}")
    private String sentinelNodes;

    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient() {
        return RedisClient.create(buildRedisUri());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient rateLimitRedisClient) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return rateLimitRedisClient.connect(codec);
    }

    @Bean
    LettuceBasedProxyManager<String> rateLimitProxyManager(
            StatefulRedisConnection<String, byte[]> rateLimitRedisConnection,
            RateLimitProperties properties) {
        Duration expiration = Duration.ofSeconds(Math.max(properties.getRefillPeriodSeconds() * 2L, 60L));
        return LettuceBasedProxyManager.builderFor(rateLimitRedisConnection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(expiration))
                .build();
    }

    private RedisURI buildRedisUri() {
        RedisURI.Builder builder;
        if (StringUtils.hasText(sentinelMaster) && StringUtils.hasText(sentinelNodes)) {
            builder = RedisURI.builder();
            for (String node : sentinelNodes.split(",")) {
                String trimmed = node.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String host = trimmed;
                int port = 26379;
                if (trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                }
                builder.withSentinel(host, port);
            }
            builder.withSentinelMasterId(sentinelMaster);
        } else {
            builder = RedisURI.builder()
                    .withHost(redisHost)
                    .withPort(redisPort);
        }
        if (StringUtils.hasText(redisPassword)) {
            builder.withPassword(redisPassword.toCharArray());
        }
        return builder.build();
    }
}
