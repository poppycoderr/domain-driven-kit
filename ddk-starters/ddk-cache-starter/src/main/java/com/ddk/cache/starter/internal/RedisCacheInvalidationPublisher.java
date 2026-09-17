package com.ddk.cache.starter.internal;

import com.ddk.cache.starter.invalidation.CacheInvalidationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 通过 Redis Pub/Sub 广播 L1 失效。
 * <p>
 * Pub/Sub 不保证送达：断连期间的消息重连后不会补发。广播只是让其他实例的 L1 尽快失效，
 * 真正的兜底是 L1 的过期时间。发布失败只记日志，不影响本次写操作。
 *
 * @author Elijah Du
 */
@Slf4j
public class RedisCacheInvalidationPublisher implements CacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final String origin = UUID.randomUUID().toString();

    public RedisCacheInvalidationPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, String channel) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    public String origin() {
        return origin;
    }

    public String channel() {
        return channel;
    }

    @Override
    public void publishEvict(String cacheName, String key) {
        publish(new CacheInvalidationMessage(origin, cacheName, key));
    }

    @Override
    public void publishClear(String cacheName) {
        publish(new CacheInvalidationMessage(origin, cacheName, null));
    }

    private void publish(CacheInvalidationMessage message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize cache invalidation message", e);
        }
        try {
            redisTemplate.convertAndSend(channel, payload);
        } catch (RuntimeException e) {
            log.warn("Failed to broadcast cache invalidation for [{}]: {}", message.cacheName(), e.toString());
        }
    }
}
