package com.ddk.cache.starter.support;

import com.ddk.cache.starter.config.DdkCacheProperties;
import com.ddk.cache.starter.invalidation.CacheInvalidationPublisher;
import com.ddk.cache.starter.metrics.CacheMetrics;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.convert.ConversionService;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 按缓存名创建 {@link TwoLevelCache}，每个缓存的 L1/L2 设置由 {@code ddk.cache.caches.<name>} 决定。
 *
 * @author Elijah Du
 */
public class DdkCacheManager implements CacheManager {

    private final ConcurrentMap<String, TwoLevelCache> caches = new ConcurrentHashMap<>();

    private final DdkCacheProperties properties;

    @Nullable
    private final Function<String, Cache> remoteCacheFactory;

    private final ConversionService conversionService;
    private final CacheInvalidationPublisher publisher;
    private final CacheMetrics metrics;

    /**
     * @param remoteCacheFactory 按缓存名创建 L2；为 null 表示没有 Redis，所有缓存只用 L1
     */
    public DdkCacheManager(DdkCacheProperties properties,
                           @Nullable Function<String, Cache> remoteCacheFactory,
                           ConversionService conversionService,
                           CacheInvalidationPublisher publisher,
                           CacheMetrics metrics) {
        this.properties = properties;
        this.remoteCacheFactory = remoteCacheFactory;
        this.conversionService = conversionService;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::createCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Set.copyOf(caches.keySet());
    }

    /**
     * 查找已创建的缓存，不存在时不创建。
     */
    public Optional<TwoLevelCache> findCache(String name) {
        return Optional.ofNullable(caches.get(name));
    }

    public boolean isRemoteAvailable() {
        return remoteCacheFactory != null;
    }

    private TwoLevelCache createCache(String name) {
        Cache remote = remoteCacheFactory == null ? null : remoteCacheFactory.apply(name);
        return new TwoLevelCache(properties.resolve(name), properties.isCacheNullValues(),
                properties.getNullValueTtl(), remote, conversionService, publisher, metrics);
    }
}
