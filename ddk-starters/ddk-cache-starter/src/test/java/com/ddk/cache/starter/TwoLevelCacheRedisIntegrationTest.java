package com.ddk.cache.starter;

import com.ddk.cache.starter.config.DdkCacheAutoConfiguration;
import com.ddk.cache.starter.support.DdkCacheManager;
import com.ddk.cache.starter.support.TwoLevelCache;
import com.ddk.redis.starter.config.DdkRedisAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 对真实 Redis 验证行为，而不是 Bean 类型：key 真的写进了 Redis、TTL 真的生效、
 * 另一个实例真的能读到、失效广播真的清掉了另一个实例的 L1。
 */
@Testcontainers(disabledWithoutDocker = true)
class TwoLevelCacheRedisIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class, DdkRedisAutoConfiguration.class,
                    DdkCacheAutoConfiguration.class, CacheAutoConfiguration.class))
            .withUserConfiguration(ServiceConfiguration.class)
            .withPropertyValues(
                    "spring.data.redis.host=" + REDIS.getHost(),
                    "spring.data.redis.port=" + REDIS.getMappedPort(6379),
                    "ddk.redis.trusted-packages=com.ddk.cache.starter",
                    "ddk.cache.key-prefix=it:cache:",
                    "ddk.cache.default-ttl=10m",
                    "ddk.cache.null-value-ttl=20s",
                    "ddk.cache.caches.products.local-enabled=true",
                    "ddk.cache.caches.prices.ttl=2m");

    @BeforeEach
    void flush() {
        runner.run(context -> redis(context).execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        }));
    }

    @Test
    void cachedResultIsStoredInRedisWithJitteredTtl() {
        runner.run(context -> {
            ProductService service = context.getBean(ProductService.class);

            assertThat(service.find(1L)).isEqualTo(product(1L));
            assertThat(service.find(1L)).isEqualTo(product(1L));
            assertThat(service.calls()).isEqualTo(1);

            StringRedisTemplate redis = redis(context);
            assertThat(redis.hasKey("it:cache:products::1")).isTrue();
            assertThat(redis.getExpire("it:cache:products::1")).isBetween(595L, 660L);
        });
    }

    @Test
    void perCacheTtlAndShortNullTtlAreApplied() {
        runner.run(context -> {
            ProductService service = context.getBean(ProductService.class);
            service.price(1L);
            service.find(404L);

            StringRedisTemplate redis = redis(context);
            assertThat(redis.getExpire("it:cache:prices::1")).isBetween(115L, 132L);
            assertThat(redis.getExpire("it:cache:products::404")).isBetween(15L, 20L);
        });
    }

    @Test
    void secondInstanceReadsFromRedisAndBackfillsItsLocalLevel() {
        runner.run(a -> runner.run(b -> {
            a.getBean(ProductService.class).find(1L);

            ProductService serviceB = b.getBean(ProductService.class);
            assertThat(serviceB.find(1L)).isEqualTo(product(1L));
            assertThat(serviceB.calls()).as("instance B must not hit the database").isZero();
            assertThat(products(b).peekLocal(1L)).isEqualTo(product(1L));
        }));
    }

    @Test
    void evictionOnOneInstanceClearsTheOtherInstancesLocalLevel() {
        runner.run(a -> runner.run(b -> {
            awaitListening(a, b);
            a.getBean(ProductService.class).find(1L);
            b.getBean(ProductService.class).find(1L);
            assertThat(products(b).peekLocal(1L)).isNotNull();

            products(a).evict(1L);

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(products(b).peekLocal(1L)).isNull());
            assertThat(redis(a).hasKey("it:cache:products::1")).isFalse();
        }));
    }

    @Test
    void clearRemovesOnlyThatCachesKeys() {
        runner.run(context -> {
            ProductService service = context.getBean(ProductService.class);
            service.find(1L);
            service.find(2L);
            service.price(1L);

            products(context).clear();

            StringRedisTemplate redis = redis(context);
            assertThat(redis.keys("it:cache:products::*")).isEmpty();
            assertThat(redis.hasKey("it:cache:prices::1")).isTrue();
        });
    }

    private static TwoLevelCache products(AssertableApplicationContext context) {
        return (TwoLevelCache) context.getBean(DdkCacheManager.class).getCache("products");
    }

    private static StringRedisTemplate redis(AssertableApplicationContext context) {
        return new StringRedisTemplate(context.getBean(RedisConnectionFactory.class));
    }

    private static void awaitListening(AssertableApplicationContext... contexts) {
        await().atMost(Duration.ofSeconds(5)).until(() -> Arrays.stream(contexts)
                .allMatch(context -> context.getBean("ddkCacheInvalidationListenerContainer", RedisMessageListenerContainer.class).isListening()));
    }

    private static Product product(Long id) {
        return new Product(id, "product-" + id, new BigDecimal("9.90"));
    }

    @Configuration(proxyBeanMethods = false)
    static class ServiceConfiguration {

        @Bean
        ProductService productService() {
            return new ProductService();
        }
    }

    public static class ProductService {

        private final AtomicInteger calls = new AtomicInteger();

        @Cacheable("products")
        public Product find(Long id) {
            calls.incrementAndGet();
            return id == 404L ? null : product(id);
        }

        @Cacheable("prices")
        public BigDecimal price(Long id) {
            return new BigDecimal("9.90");
        }

        public int calls() {
            return calls.get();
        }
    }
}
