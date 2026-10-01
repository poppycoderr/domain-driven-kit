package com.ddk.concurrency.starter.config;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 并发控制配置。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkConcurrencyProperties.PREFIX)
public class DdkConcurrencyProperties {

    public static final String PREFIX = "ddk.concurrency";

    /**
     * 是否启用。关闭后 {@code @AggregateLock} 与 {@code @Idempotent} 不再生效，仅用于本地排查。
     */
    private boolean enabled = true;

    /**
     * Redis key 的前缀。多个应用共用一个 Redis 时用它区分。
     */
    private String keyPrefix = "ddk:";

    private Lock lock = new Lock();

    private IdempotentRequests idempotent = new IdempotentRequests();

    @Data
    public static class Lock {

        /**
         * 等待锁的默认最长时间。
         */
        private Duration waitTime = Duration.ofSeconds(3);

        /**
         * 持有锁的默认最长时间。不设置时由 Redisson 的看门狗自动续期，直到操作结束。
         */
        private @Nullable Duration leaseTime;
    }

    @Data
    public static class IdempotentRequests {

        /**
         * 请求登记的默认保留时间。
         */
        private Duration ttl = Duration.ofMinutes(10);
    }
}
