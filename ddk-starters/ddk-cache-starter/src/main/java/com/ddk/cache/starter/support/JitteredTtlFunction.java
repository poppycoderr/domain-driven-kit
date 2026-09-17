package com.ddk.cache.starter.support;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L2 过期时间：null 值用短 TTL；正常值在 TTL 上随机延长，避免同一批写入同时过期。
 * <p>
 * 只延长不缩短，保证实际过期时间不会低于配置值。
 *
 * @author Elijah Du
 */
public record JitteredTtlFunction(Duration ttl, double jitter, Duration nullValueTtl)
        implements RedisCacheWriter.TtlFunction {

    public JitteredTtlFunction {
        if (jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("ttl jitter must be within [0, 1], got " + jitter);
        }
    }

    @Override
    public Duration getTimeToLive(Object key, @Nullable Object value) {
        if (value == null || value instanceof NullValue) {
            return nullValueTtl;
        }
        if (ttl.isZero() || ttl.isNegative()) {
            return NO_EXPIRATION;
        }
        long extra = (long) (ttl.toMillis() * jitter * ThreadLocalRandom.current().nextDouble());
        return ttl.plusMillis(extra);
    }
}
