package com.ddk.cache.starter.internal;

import com.ddk.cache.starter.config.DdkCacheProperties;
import com.ddk.cache.starter.invalidation.CacheInvalidationPublisher;
import com.ddk.cache.starter.metrics.CacheMetrics;
import com.ddk.cache.starter.support.DdkCacheManager;
import com.ddk.cache.starter.support.TwoLevelCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 一级缓存失效广播的跨版本兼容：{@code compat/0.10.0/redis.json} 里的两条消息由 DDK 0.10.0 写出（见仓库的 {@code compat/generator}）。
 * 滚动发布时新旧实例在同一个频道上互发，当前版本要处理旧消息，发出的消息旧版本也要能处理。
 */
@DisplayName("与 0.10.0 的缓存失效消息兼容")
class CacheMessageCompatibilityTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("当前版本处理 0.10.0 发出的删除和清空消息")
    void handlesMessagesFrom010() throws IOException {
        JsonNode fixture = fixture();
        DdkCacheManager manager = new DdkCacheManager(new DdkCacheProperties(), null, new DefaultConversionService(),
                CacheInvalidationPublisher.NOOP, CacheMetrics.NOOP);
        CacheInvalidationListener listener = new CacheInvalidationListener(manager, mapper, "instance-b");
        TwoLevelCache cache = (TwoLevelCache) manager.getCache("products");
        cache.put("sku-1", "first");
        cache.put("sku-2", "second");

        listener.onMessage(message(fixture.get("evictMessage").asString()), null);
        assertThat(cache.peekLocal("sku-1")).isNull();
        assertThat(cache.peekLocal("sku-2")).isEqualTo("second");

        listener.onMessage(message(fixture.get("clearMessage").asString()), null);
        assertThat(cache.peekLocal("sku-2")).isNull();
    }

    @Test
    @DisplayName("当前版本发出的消息与 0.10.0 的字段和取值相同，只有发送实例的标识不同")
    void publishesTheSameMessages() throws IOException {
        JsonNode fixture = fixture();
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisCacheInvalidationPublisher publisher = new RedisCacheInvalidationPublisher(template, mapper, "ddk:cache:invalidation");

        publisher.publishEvict("products", "sku-1");
        publisher.publishClear("products");

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(template, times(2)).convertAndSend(eq("ddk:cache:invalidation"), sent.capture());
        assertThat(mapper.readTree(sent.getAllValues().get(0))).isEqualTo(withOrigin(fixture.get("evictMessage").asString(), publisher.origin()));
        assertThat(mapper.readTree(sent.getAllValues().get(1))).isEqualTo(withOrigin(fixture.get("clearMessage").asString(), publisher.origin()));
    }

    private JsonNode withOrigin(String json, String origin) {
        ObjectNode node = (ObjectNode) mapper.readTree(json);
        node.put("origin", origin);
        return node;
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage("ddk:cache:invalidation".getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode fixture() throws IOException {
        try (InputStream in = new ClassPathResource("compat/0.10.0/redis.json").getInputStream()) {
            return mapper.readTree(in);
        }
    }
}
