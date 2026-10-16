package com.ddk.concurrency.starter.internal;

import org.jspecify.annotations.Nullable;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.UUID;

/**
 * 登记处理中或已处理的请求：{@code SET key token NX PX ttl}，登记成功的那一个请求才能执行。
 * <p>
 * 每次登记写入一个新的随机令牌，撤销时只删除值仍是自己令牌的那一条（Redisson 的 compare-and-set 在 Redis 里原子执行）。
 * 只按 key 删除的话，一个执行超过 TTL 的请求在失败时会删掉后来者的登记，第三个相同的请求就能进来。
 */
public class RequestRegistry {

    private final RedissonClient redisson;

    private final String keyPrefix;

    public RequestRegistry(RedissonClient redisson, String keyPrefix) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix;
    }

    /**
     * @return 登记成功时返回这次登记，用于失败后撤销；已经有人登记时返回 {@code null}
     */
    public @Nullable Registration register(String scope, String key, Duration ttl) {
        Registration registration = new Registration(name(scope, key), UUID.randomUUID().toString());
        boolean registered = redisson.<String>getBucket(registration.name(), StringCodec.INSTANCE).setIfAbsent(registration.token(), ttl);
        return registered ? registration : null;
    }

    /**
     * 撤销自己的登记。登记已经过期、并被别的请求重新登记时什么都不做。
     *
     * @return 是否删除了自己的登记
     */
    public boolean release(Registration registration) {
        return redisson.<String>getBucket(registration.name(), StringCodec.INSTANCE).compareAndSet(registration.token(), null);
    }

    String name(String scope, String key) {
        return keyPrefix + "idempotent:" + scope + ":" + key;
    }

    /**
     * 一次登记：Redis key 与写入的令牌。
     */
    public record Registration(
            String name,

            String token
    ) {
    }
}
