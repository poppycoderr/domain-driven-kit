package com.ddk.redis.starter.serializer;

import com.ddk.compat.CachedProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 里的值的跨版本兼容：{@code compat/0.10.0/redis.json} 的 {@code cachedValue} 是 DDK 0.10.0 的值序列化器写出的字节
 * （见仓库的 {@code compat/generator}）。类型信息写在 {@code @class} 和数组形式的类型标注里，当前版本读写都要与它一致。
 */
@DisplayName("与 0.10.0 的 Redis 值兼容")
class RedisValueCompatibilityTest {

    private final GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(
            RedisJsonMapper.create(List.of("com.ddk.compat")));

    private final CachedProduct product = new CachedProduct("sku-1", new BigDecimal("19.90"), Instant.parse("2026-10-22T08:00:00Z"),
            List.of("new", "sale"), null);

    @Test
    @DisplayName("当前版本读得回 0.10.0 写入的值，写出的字节也相同")
    void readsAndWritesTheSameBytes() throws IOException {
        String stored = fixture().get("cachedValue").asString();

        Object read = serializer.deserialize(stored.getBytes(StandardCharsets.UTF_8));
        assertThat(read).isInstanceOf(CachedProduct.class);
        CachedProduct value = (CachedProduct) read;
        assertThat(value.price()).isEqualByComparingTo(product.price());
        assertThat(value).usingRecursiveComparison().ignoringFields("price").isEqualTo(product);
        assertThat(new String(serializer.serialize(product), StandardCharsets.UTF_8)).isEqualTo(stored);
    }

    private static JsonNode fixture() throws IOException {
        try (InputStream in = new ClassPathResource("compat/0.10.0/redis.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(in);
        }
    }
}
