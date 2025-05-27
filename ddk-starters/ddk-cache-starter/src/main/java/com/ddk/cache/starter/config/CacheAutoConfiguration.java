package com.ddk.cache.starter.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@AutoConfiguration
@EnableCaching
@ConditionalOnClass({CacheManager.class, Caffeine.class, RedisConnectionFactory.class})
public class CacheAutoConfiguration {

    // --- Caffeine CacheManager (L1) ---
    @Bean("caffeineCacheManager")
    @ConditionalOnProperty(prefix = "spring.cache.caffeine", name = "spec")
    @ConditionalOnMissingBean(name = "caffeineCacheManager")
    public CacheManager caffeineCacheManager(CacheProperties cacheProperties) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        String caffeineSpec = cacheProperties.getCaffeine().getSpec();
        if (StringUtils.hasText(caffeineSpec)) {
            caffeineCacheManager.setCacheSpecification(caffeineSpec);
        } else {
            // Sensible default if no spec is provided
            caffeineCacheManager.setCacheSpecification("maximumSize=1000,expireAfterWrite=10m");
        }

        List<String> cacheNames = cacheProperties.getCacheNames();
        if (cacheNames != null && !cacheNames.isEmpty()) {
            caffeineCacheManager.setCacheNames(cacheNames);
        }
        // If cacheNames is empty, CaffeineCacheManager allows dynamic cache creation by default.
        return caffeineCacheManager;
    }

    // --- Redis CacheManager (L2) ---
    @Bean("redisCacheManager")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "spring.data.redis", name = "host") // Check if Redis is configured
    @ConditionalOnMissingBean(name = "redisCacheManager")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory, CacheProperties cacheProperties) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig();
        CacheProperties.Redis redisProperties = cacheProperties.getRedis();

        if (redisProperties.getTimeToLive() != null) {
            defaultCacheConfig = defaultCacheConfig.entryTtl(redisProperties.getTimeToLive());
        }
        if (StringUtils.hasText(redisProperties.getKeyPrefix())) {
            defaultCacheConfig = defaultCacheConfig.prefixCacheNameWith(redisProperties.getKeyPrefix());
        }
        if (!redisProperties.isCacheNullValues()) {
            defaultCacheConfig = defaultCacheConfig.disableCachingNullValues();
        }
        if (!redisProperties.isUseKeyPrefix()) {
            // This is a bit tricky as prefixCacheNameWith(null) or "" might not achieve "no prefix"
            // Depending on version, defaultCacheConfig.usePrefix() or custom logic might be needed.
            // For simplicity, we rely on prefixCacheNameWith being non-empty if a prefix is desired.
            // If no key prefix is set in properties, Spring Boot's default is usually no prefix or "::"
        }
        // Note: `cacheProperties.getRedis().determineKeyPrefix()` could be used if available in the Spring Boot version.

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfig);

        List<String> cacheNames = cacheProperties.getCacheNames();
        if (cacheNames != null && !cacheNames.isEmpty()) {
            builder.initialCacheNames(cacheNames.stream().collect(Collectors.toSet()));
        }
        // RedisCacheManager can also create caches on-the-fly if not initially specified.
        return builder.build();
    }

    // --- Primary CompositeCacheManager ---
    @Bean
    @Primary
    public CacheManager cacheManager(
            @Autowired(required = false) @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            @Autowired(required = false) @Qualifier("redisCacheManager") CacheManager redisCacheManager,
            CacheProperties cacheProperties) { // Inject CacheProperties for fallback CacheManager

        List<CacheManager> managers = new ArrayList<>();
        if (caffeineCacheManager != null) {
            managers.add(caffeineCacheManager); // L1
        }
        if (redisCacheManager != null) {
            managers.add(redisCacheManager); // L2
        }

        if (managers.isEmpty()) {
            // Fallback to a simple ConcurrentMapCacheManager if no other cache managers are configured
            // Or, let Spring Boot's default behavior take over if this bean isn't created.
            // For explicit fallback:
            ConcurrentMapCacheManager fallbackManager = new ConcurrentMapCacheManager();
            List<String> cacheNames = cacheProperties.getCacheNames();
            if (cacheNames != null && !cacheNames.isEmpty()) {
                fallbackManager.setCacheNames(cacheNames);
            }
            return fallbackManager;
        }

        if (managers.size() == 1) {
            return managers.get(0);
        }

        CompositeCacheManager compositeCacheManager = new CompositeCacheManager(managers.toArray(new CacheManager[0]));
        // compositeCacheManager.setFallbackToNoOpCache(false); // default is false, which is usually desired
        return compositeCacheManager;
    }
}
