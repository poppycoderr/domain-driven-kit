package com.ddk.cache.starter.invalidation;

import com.ddk.cache.starter.config.DdkCacheProperties;
import com.ddk.cache.starter.metrics.CacheMetrics;
import com.ddk.cache.starter.support.DdkCacheManager;
import com.ddk.cache.starter.support.TwoLevelCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CacheInvalidationListenerTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final DdkCacheManager manager = new DdkCacheManager(new DdkCacheProperties(), null,
            new DefaultConversionService(), CacheInvalidationPublisher.NOOP, CacheMetrics.NOOP);
    private final CacheInvalidationListener listener = new CacheInvalidationListener(manager, mapper, "me");

    @Test
    void messageWrittenByPublisherIsReadableByListener() throws Exception {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisCacheInvalidationPublisher publisher = new RedisCacheInvalidationPublisher(template, mapper, "ch");
        TwoLevelCache cache = (TwoLevelCache) manager.getCache("users");
        cache.put("1", "alice");
        cache.put("2", "bob");

        publisher.publishEvict("users", "1");
        listener.onMessage(message(captured(template)), null);
        assertThat(cache.peekLocal("1")).isNull();
        assertThat(cache.peekLocal("2")).isEqualTo("bob");

        listener.onMessage(message(mapper.writeValueAsString(
                new CacheInvalidationMessage("other", "users", null))), null);
        assertThat(cache.peekLocal("2")).isNull();
    }

    @Test
    void ignoresOwnMessages() throws Exception {
        TwoLevelCache cache = (TwoLevelCache) manager.getCache("users");
        cache.put("1", "alice");

        listener.onMessage(message(mapper.writeValueAsString(new CacheInvalidationMessage("me", "users", "1"))), null);

        assertThat(cache.peekLocal("1")).isEqualTo("alice");
    }

    @Test
    void doesNotCreateCachesThisInstanceNeverUsed() throws Exception {
        listener.onMessage(message(mapper.writeValueAsString(new CacheInvalidationMessage("other", "orders", "1"))), null);

        assertThat(manager.getCacheNames()).isEmpty();
    }

    private static String captured(StringRedisTemplate template) {
        org.mockito.ArgumentCaptor<String> payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(template).convertAndSend(eq("ch"), payload.capture());
        return payload.getValue();
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage("ch".getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }
}
