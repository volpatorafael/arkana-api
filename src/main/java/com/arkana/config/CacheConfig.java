package com.arkana.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class CacheConfig {
    public static final String PUBLIC_BILLING_PLANS = "public-billing-plans";
    public static final String LOCALIZED_SPREADS = "localized-spreads";

    @Bean
    @Primary
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(PUBLIC_BILLING_PLANS);
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(5)
            .expireAfterWrite(Duration.ofMinutes(3)));
        return manager;
    }

    @Bean("spreadCacheManager")
    CacheManager spreadCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(LOCALIZED_SPREADS);
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(30)
            .expireAfterWrite(Duration.ofHours(2)));
        return manager;
    }
}
