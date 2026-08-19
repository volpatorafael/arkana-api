package com.arkana.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class IdentityAnalyticsCacheConfig {
  @Bean("identityAnalyticsCacheManager")
  CacheManager identityAnalyticsCacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager("supabase-identity-users");
    manager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(Duration.ofSeconds(90)));
    return manager;
  }
}

