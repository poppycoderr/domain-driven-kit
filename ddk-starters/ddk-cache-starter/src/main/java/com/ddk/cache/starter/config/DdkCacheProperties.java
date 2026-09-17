package com.ddk.cache.starter.config;

import com.ddk.cache.starter.support.CacheSettings;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 二级缓存配置。
 * <p>
 * 刻意不复用 {@code spring.cache.*}：那组配置属于 Spring Boot 自己的缓存自动配置，
 * 复用它意味着依赖一个会被本 starter 顶掉的自动配置所注册的 Bean。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkCacheProperties.PREFIX)
public class DdkCacheProperties {

    public static final String PREFIX = "ddk.cache";

    /**
     * 是否启用 DDK 二级缓存。关闭后由 Spring Boot 自己的缓存自动配置接管。
     */
    private boolean enabled = true;

    /**
     * L2（Redis）默认过期时间。设为 0 表示不过期。
     */
    private Duration defaultTtl = Duration.ofMinutes(30);

    /**
     * L2 过期时间的随机延长比例，取值 [0, 1]。
     * <p>
     * 批量写入的数据会在同一时刻过期，同时回源压垮数据库。0.1 表示在 TTL 基础上随机延长 0~10%。
     */
    private double ttlJitter = 0.1;

    /**
     * 是否缓存 null 返回值，用于防缓存穿透。
     */
    private boolean cacheNullValues = true;

    /**
     * null 值的过期时间，对 L1 和 L2 同时生效，应显著短于正常值。
     */
    private Duration nullValueTtl = Duration.ofMinutes(1);

    /**
     * Redis key 前缀，完整 key 为 {@code <keyPrefix><cacheName>::<key>}。
     * 多个应用共用一个 Redis 时必须区分。失效广播的频道也以它为前缀。
     */
    private String keyPrefix = "ddk:cache:";

    private final Local local = new Local();

    /**
     * 按缓存名覆盖默认值。
     */
    private Map<String, CacheSpec> caches = new LinkedHashMap<>();

    public CacheSettings resolve(String name) {
        CacheSpec spec = caches.getOrDefault(name, CacheSpec.EMPTY);
        return new CacheSettings(
                name,
                spec.getTtl() != null ? spec.getTtl() : defaultTtl,
                spec.getLocalEnabled() != null ? spec.getLocalEnabled() : local.isEnabled(),
                spec.getLocalTtl() != null ? spec.getLocalTtl() : local.getTtl(),
                spec.getLocalMaximumSize() != null ? spec.getLocalMaximumSize() : local.getMaximumSize());
    }

    /**
     * 是否有任何缓存会启用 L1。决定要不要订阅失效广播。
     */
    public boolean anyLocalEnabled() {
        return local.isEnabled()
                || caches.values().stream().anyMatch(spec -> Boolean.TRUE.equals(spec.getLocalEnabled()));
    }

    @Data
    public static class Local {

        /**
         * 是否默认启用 L1（Caffeine）。
         * <p>
         * 默认关闭：L1 在各实例间互相独立，启用就引入了跨实例的短暂不一致。
         * 读多写少、能容忍秒级旧值的缓存再按名字单独打开。
         * Redis 不可用时所有缓存都会退化为只用 L1，不受这个开关影响。
         */
        private boolean enabled = false;

        /**
         * L1 过期时间。它同时是跨实例不一致窗口的上限，保持远短于 L2。
         */
        private Duration ttl = Duration.ofSeconds(30);

        /**
         * 每个缓存的 L1 最大条目数。
         */
        private long maximumSize = 10_000;

        /**
         * 启用 L1 时，是否通过 Redis Pub/Sub 通知其他实例清理各自的 L1。
         * 广播不保证送达，L1 过期时间始终是最终兜底。
         */
        private boolean broadcastEvict = true;
    }

    @Data
    public static class CacheSpec {

        static final CacheSpec EMPTY = new CacheSpec();

        /**
         * 该缓存的 L2 过期时间。
         */
        private @Nullable Duration ttl;

        /**
         * 该缓存是否启用 L1。
         */
        private @Nullable Boolean localEnabled;

        /**
         * 该缓存的 L1 过期时间。
         */
        private @Nullable Duration localTtl;

        /**
         * 该缓存的 L1 最大条目数。
         */
        private @Nullable Long localMaximumSize;
    }
}
