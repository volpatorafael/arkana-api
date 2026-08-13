package com.arkana.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void shouldKeepLocalizedSpreadsForTwoHoursWithRoomForThirtyLocales() {
        CacheManager manager = new CacheConfig().spreadCacheManager();
        CaffeineCache springCache = (CaffeineCache) manager.getCache(CacheConfig.LOCALIZED_SPREADS);

        assertThat(springCache).isNotNull();
        Cache<Object, Object> nativeCache = springCache.getNativeCache();
        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(30);
        assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
            .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void shouldPreserveTheExistingPublicPlansCachePolicy() {
        CacheManager manager = new CacheConfig().cacheManager();
        CaffeineCache springCache = (CaffeineCache) manager.getCache(CacheConfig.PUBLIC_BILLING_PLANS);

        assertThat(springCache).isNotNull();
        Cache<Object, Object> nativeCache = springCache.getNativeCache();
        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(5);
        assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
            .isEqualTo(Duration.ofMinutes(3));
    }
}
