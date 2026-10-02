package com.ddk.concurrency.starter.internal;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * 基于 Redisson {@code RRateLimiter} 的限流计数，所有实例共享。
 * <p>
 * 阈值是 key 名的一部分：改了注解上的次数或窗口，新版本用的是新的 key，不会沿用 Redis 里旧的配置；旧 key 在闲置两个窗口后过期。
 */
public class RateLimiters {

    private final RedissonClient redisson;

    private final String keyPrefix;

    public RateLimiters(RedissonClient redisson, String keyPrefix) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix;
    }

    public boolean tryAcquire(String scope, String key, long limit, Duration period) {
        RRateLimiter limiter = redisson.getRateLimiter(name(scope, key, limit, period));
        limiter.trySetRate(RateType.OVERALL, limit, period, period.multipliedBy(2));
        return limiter.tryAcquire();
    }

    String name(String scope, String key, long limit, Duration period) {
        return keyPrefix + "rate:" + scope + ":" + limit + "/" + period.toMillis() + "ms:" + key;
    }
}
