package com.ddk.redis.starter.serializer;

import com.ddk.redis.starter.config.fixture.CachedUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisJsonMapperTest {

    private final GenericJackson2JsonRedisSerializer trusting =
            new GenericJackson2JsonRedisSerializer(RedisJsonMapper.create(List.of("com.ddk.redis.starter.config.fixture")));

    private final GenericJackson2JsonRedisSerializer jdkOnly =
            new GenericJackson2JsonRedisSerializer(RedisJsonMapper.create(List.of()));

    @Test
    void roundTripsRecordFromTrustedPackage() {
        CachedUser user = new CachedUser(1L, "alice", LocalDateTime.of(2026, 1, 2, 3, 4, 5),
                new ArrayList<>(List.of("admin")));

        assertThat(trusting.deserialize(trusting.serialize(user))).isEqualTo(user);
    }

    @Test
    void roundTripsJdkValues() {
        Map<String, Object> value = new HashMap<>();
        value.put("amount", new BigDecimal("12.50"));
        value.put("at", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        value.put("tags", new ArrayList<>(List.of("a", "b")));

        assertThat(jdkOnly.deserialize(jdkOnly.serialize(value))).isEqualTo(value);
        assertThat(jdkOnly.deserialize(jdkOnly.serialize("plain"))).isEqualTo("plain");
        // 标量不带类型信息，数字按 JSON 数值读回，Long 小值会变成 Integer
        assertThat(jdkOnly.deserialize(jdkOnly.serialize(42))).isEqualTo(42);
    }

    @Test
    void rejectsTypeOutsideTrustedPackages() {
        byte[] payload = trusting.serialize(new CachedUser(1L, "alice", null, null));

        assertThatThrownBy(() -> jdkOnly.deserialize(payload))
                .isInstanceOf(SerializationException.class)
                .hasStackTraceContaining("PolymorphicTypeValidator");
    }

    @Test
    void rejectsInjectedTypeThatJacksonDoesNotBlocklist() {
        // Jackson 内置黑名单只覆盖已知 gadget；白名单要能拦住黑名单之外的类型
        byte[] payload = """
                {"@class":"org.springframework.core.io.FileSystemResource","path":"/etc/passwd"}
                """.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> trusting.deserialize(payload))
                .isInstanceOf(SerializationException.class)
                .hasStackTraceContaining("PolymorphicTypeValidator");
    }
}
