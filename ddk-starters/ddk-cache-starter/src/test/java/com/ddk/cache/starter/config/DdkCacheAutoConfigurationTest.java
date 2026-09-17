package com.ddk.cache.starter.config;

import com.ddk.cache.starter.metrics.MicrometerCacheMetrics;
import com.ddk.cache.starter.support.DdkCacheManager;
import com.ddk.cache.starter.support.TwoLevelCache;
import com.ddk.redis.starter.config.DdkRedisAutoConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DdkCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkCacheAutoConfiguration.class, CacheAutoConfiguration.class));

    private ApplicationContextRunner withRedis() {
        return runner.withConfiguration(AutoConfigurations.of(DdkRedisAutoConfiguration.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));
    }

    @Test
    void fallsBackToLocalOnlyCacheWithoutRedis() {
        runner.withUserConfiguration(ServiceConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            DdkCacheManager manager = context.getBean(DdkCacheManager.class);
            assertThat(manager.isRemoteAvailable()).isFalse();
            assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);

            CachedService service = context.getBean(CachedService.class);
            service.find("1");
            service.find("1");
            assertThat(service.calls()).isEqualTo(1);
            assertThat(((TwoLevelCache) manager.getCache("users")).hasLocal()).isTrue();
        });
    }

    @Test
    void usesRedisAsSecondLevelWhenConnectionFactoryExists() {
        withRedis().run(context -> {
            DdkCacheManager manager = context.getBean(DdkCacheManager.class);
            assertThat(manager.isRemoteAvailable()).isTrue();

            TwoLevelCache cache = (TwoLevelCache) manager.getCache("users");
            assertThat(cache.hasRemote()).isTrue();
            assertThat(cache.hasLocal()).as("L1 is off by default").isFalse();
            // 没有任何缓存启用 L1，就不占用订阅连接
            assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
        });
    }

    @Test
    void enablesLocalLevelPerCache() {
        withRedis()
                .withPropertyValues("ddk.cache.caches.dictionary.local-enabled=true",
                        "ddk.cache.local.broadcast-evict=false")
                .run(context -> {
                    DdkCacheManager manager = context.getBean(DdkCacheManager.class);
                    assertThat(((TwoLevelCache) manager.getCache("dictionary")).hasLocal()).isTrue();
                    assertThat(((TwoLevelCache) manager.getCache("users")).hasLocal()).isFalse();
                });
    }

    @Test
    void backsOffWhenDisabled() {
        runner.withUserConfiguration(ServiceConfiguration.class)
                .withPropertyValues("ddk.cache.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DdkCacheManager.class));
    }

    @Test
    void backsOffWhenApplicationDefinesCacheManager() {
        runner.withBean(CacheManager.class, ConcurrentMapCacheManager::new).run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).doesNotHaveBean(DdkCacheManager.class);
        });
    }

    @Test
    void recordsMetricsWhenMeterRegistryExists() {
        runner.withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
            assertThat(context).hasSingleBean(MicrometerCacheMetrics.class);

            context.getBean(DdkCacheManager.class).getCache("users").get("missing");

            assertThat(context.getBean(SimpleMeterRegistry.class)
                    .get("ddk.cache.access").tag("cache", "users").tag("result", "miss").counter().count())
                    .isEqualTo(1.0);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ServiceConfiguration {

        @Bean
        CachedService cachedService() {
            return new CachedService();
        }
    }

    public static class CachedService {

        private final AtomicInteger calls = new AtomicInteger();

        @Cacheable("users")
        public String find(String id) {
            calls.incrementAndGet();
            return "user-" + id;
        }

        public int calls() {
            return calls.get();
        }
    }
}
