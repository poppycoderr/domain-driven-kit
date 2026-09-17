package com.ddk.cache.starter.internal;

import com.ddk.cache.starter.metrics.CacheMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 输出到 Micrometer 的缓存指标：
 * <ul>
 *     <li>{@code ddk.cache.access{cache, result=l1_hit|l2_hit|miss}}</li>
 *     <li>{@code ddk.cache.remote.errors{cache, operation}}</li>
 * </ul>
 * {@code MeterRegistry} 通常由 actuator 在本 starter 之后注册，所以在第一次记录时才去取。
 *
 * @author Elijah Du
 */
public class MicrometerCacheMetrics implements CacheMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;

    private volatile @Nullable MeterRegistry registry;

    public MicrometerCacheMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public void access(String cacheName, AccessResult result) {
        MeterRegistry r = registry();
        if (r != null) {
            Counter.builder("ddk.cache.access")
                    .tag("cache", cacheName)
                    .tag("result", result.tag())
                    .register(r)
                    .increment();
        }
    }

    @Override
    public void remoteError(String cacheName, String operation) {
        MeterRegistry r = registry();
        if (r != null) {
            Counter.builder("ddk.cache.remote.errors")
                    .tag("cache", cacheName)
                    .tag("operation", operation)
                    .register(r)
                    .increment();
        }
    }

    private @Nullable MeterRegistry registry() {
        MeterRegistry r = registry;
        if (r == null) {
            r = registryProvider.getIfAvailable();
            registry = r;
        }
        return r;
    }
}
