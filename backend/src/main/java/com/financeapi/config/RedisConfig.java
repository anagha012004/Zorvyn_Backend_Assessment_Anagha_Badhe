package com.financeapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisURI uri = RedisURI.create(redisUrl);

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(uri.getHost());
        serverConfig.setPort(uri.getPort());
        if (uri.getPassword() != null && uri.getPassword().length > 0) {
            serverConfig.setPassword(new String(uri.getPassword()));
        }
        if (uri.getUsername() != null && !uri.getUsername().isBlank()) {
            serverConfig.setUsername(uri.getUsername());
        }

        LettuceClientConfiguration clientConfig;
        if (redisUrl.startsWith("rediss://")) {
            clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(10))
                    .useSsl().disablePeerVerification().and()
                    .build();
        } else {
            clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(10))
                    .build();
        }

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    /**
     * Use simple in-memory cache instead of Redis for application caches.
     * This eliminates all Jackson deserialization 500s caused by Redis
     * trying to deserialize complex DTOs (TransactionResponse, BigDecimal maps)
     * back from JSON with type metadata mismatches.
     *
     * Redis connection is still available for DataInitializer to flush on startup.
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("dashboard-summary", "monthly-trends");
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
