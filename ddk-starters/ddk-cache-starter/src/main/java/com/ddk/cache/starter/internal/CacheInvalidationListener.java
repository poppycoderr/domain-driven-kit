package com.ddk.cache.starter.internal;

import com.ddk.cache.starter.support.DdkCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 接收其他实例的失效广播，只清本地 L1，不再转发。
 * <p>
 * 本实例还没用过的缓存不会因为收到消息而被创建。
 *
 * @author Elijah Du
 */
@Slf4j
public class CacheInvalidationListener implements MessageListener {

    private final DdkCacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final String origin;

    public CacheInvalidationListener(DdkCacheManager cacheManager, ObjectMapper objectMapper, String origin) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        this.origin = origin;
    }

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        CacheInvalidationMessage event;
        try {
            event = objectMapper.readValue(message.getBody(), CacheInvalidationMessage.class);
        } catch (JacksonException e) {
            log.warn("Ignoring malformed cache invalidation message: {}", e.getMessage());
            return;
        }
        if (origin.equals(event.origin())) {
            return;
        }
        String key = event.key();
        cacheManager.findCache(event.cacheName()).ifPresent(cache -> {
            if (key == null) {
                cache.clearLocal();
            } else {
                cache.evictLocal(key);
            }
        });
    }
}
