package com.ddk.concurrency.starter.internal;

import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;

/**
 * 登记处理中或已处理的请求：{@code SET key NX} 加过期时间，登记成功的那一个请求才能执行。
 */
public class RequestRegistry {

    private final RedissonClient redisson;

    private final String keyPrefix;

    public RequestRegistry(RedissonClient redisson, String keyPrefix) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix;
    }

    public boolean register(String scope, String key, Duration ttl) {
        return redisson.<String>getBucket(name(scope, key), StringCodec.INSTANCE).setIfAbsent("1", ttl);
    }

    public void release(String scope, String key) {
        redisson.getBucket(name(scope, key), StringCodec.INSTANCE).delete();
    }

    String name(String scope, String key) {
        return keyPrefix + "idempotent:" + scope + ":" + key;
    }
}
