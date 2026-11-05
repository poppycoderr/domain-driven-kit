package com.ddk.concurrency.starter.internal;

import com.ddk.compat.ShipmentDesk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 防重复提交和限流 key 的跨版本兼容：{@code compat/0.10.0/concurrency-keys.json} 是 DDK 0.10.0 为
 * {@code ShipmentDesk#dispatch(String, Date)} 算出的默认 scope 和两种 key（见仓库的 {@code compat/generator}）。
 * <p>
 * 0.11 有意改了默认 scope（参数写完整类型名，Breaking），key 的结构没有变：同一个 scope 和 key 得到同一个 Redis key，
 * 所以把旧 scope 显式写在注解上就能在滚动发布时沿用 0.10 的登记和限流额度。
 */
@DisplayName("与 0.10.0 的防重复提交和限流 key 兼容")
class ConcurrencyKeyCompatibilityTest {

    private static final Pattern FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

    @Test
    @DisplayName("scope 相同时，当前版本写出的 key 与 0.10.0 相同")
    void sameScopeGivesTheSameKeys() throws IOException {
        Map<String, String> released = fixture();
        RedissonClient redisson = mock(RedissonClient.class);

        assertThat(new RequestRegistry(redisson, "ddk:").name(released.get("defaultScope"), "req-1")).isEqualTo(released.get("idempotentKey"));
        assertThat(new RateLimiters(redisson, "ddk:").name(released.get("defaultScope"), "tenant-a", 20, Duration.ofMinutes(1)))
                .isEqualTo(released.get("rateKey"));
    }

    @Test
    @DisplayName("默认 scope 有意改变：只是参数从简单类名改成完整类型名，这是 CHANGELOG 里记录的 Breaking")
    void defaultScopeNamesParameterTypesInFull() throws Exception {
        Map<String, String> released = fixture();
        Method dispatch = ShipmentDesk.class.getMethod("dispatch", String.class, Date.class);

        assertThat(released.get("defaultScope")).isEqualTo("com.ddk.compat.ShipmentDesk#dispatch(String,Date)");
        assertThat(ConcurrencyInterceptor.defaultScope(dispatch)).isEqualTo("com.ddk.compat.ShipmentDesk#dispatch(java.lang.String,java.util.Date)");
    }

    private static Map<String, String> fixture() throws IOException {
        String text = new ClassPathResource("compat/0.10.0/concurrency-keys.json").getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD.matcher(text);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        assertThat(fields).containsOnlyKeys("defaultScope", "idempotentKey", "rateKey");
        return fields;
    }
}
