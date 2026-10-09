package com.edocs.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

// In-process caches for hot, read-mostly data; each has its own TTL.
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TEMPLATES = "templates";
    public static final String SETTINGS = "settings";
    public static final String DASHBOARD = "dashboard";
    public static final String ARCHIVE_METRICS = "archiveMetrics";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(com.edocs.security.PrincipalService.CACHE, build(Duration.ofSeconds(30), 10_000));
        manager.registerCustomCache(TEMPLATES, build(Duration.ofMinutes(10), 1_000));
        manager.registerCustomCache(SETTINGS, build(Duration.ofMinutes(10), 1_000));
        manager.registerCustomCache(DASHBOARD, build(Duration.ofSeconds(30), 1_000));
        manager.registerCustomCache(ARCHIVE_METRICS, build(Duration.ofSeconds(60), 1_000));
        return manager;
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> build(Duration ttl, long max) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(max).recordStats().build();
    }
}
