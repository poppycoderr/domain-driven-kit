package com.ddk.concurrency.starter.internal;

import com.ddk.compat.Releases;
import com.ddk.compat.ShipmentDesk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 防重复提交和限流 key 的跨版本兼容：每个已发布版本的 {@code compat/<版本>/concurrency-keys.json} 是那个版本为
 * {@code ShipmentDesk#dispatch(String, Date)} 算出的默认 scope 和两种 key（见仓库的 {@code compat/generator}）。
 * <p>
 * key 的结构在各版本之间不变：同一个 scope 和 key 得到同一个 Redis key，所以把旧 scope 显式写在注解上就能沿用旧版本的登记和额度。
 * 默认 scope 的有意改变记在 {@link #DELIBERATE_SCOPE_CHANGES} 里；其余版本的默认 scope 必须与当前版本相同。
 */
@DisplayName("与已发布版本的防重复提交和限流 key 兼容")
class ConcurrencyKeyCompatibilityTest {

    /**
     * 默认 scope 与当前版本不同的已发布版本，以及它们的默认 scope。0.11 起参数写完整类型名（CHANGELOG 0.11.0 的 Breaking）。
     */
    private static final Map<String, String> DELIBERATE_SCOPE_CHANGES = Map.of("0.10.0", "com.ddk.compat.ShipmentDesk#dispatch(String,Date)");

    private static final Pattern FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.ddk.compat.Releases#versions")
    @DisplayName("scope 相同时，当前版本写出的 key 与已发布版本相同")
    void sameScopeGivesTheSameKeys(String version) {
        Map<String, String> released = fixture(version);
        RedissonClient redisson = mock(RedissonClient.class);

        assertThat(new RequestRegistry(redisson, "ddk:").name(released.get("defaultScope"), "req-1")).isEqualTo(released.get("idempotentKey"));
        assertThat(new RateLimiters(redisson, "ddk:").name(released.get("defaultScope"), "tenant-a", 20, Duration.ofMinutes(1)))
                .isEqualTo(released.get("rateKey"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.ddk.compat.Releases#versions")
    @DisplayName("默认 scope 与已发布版本相同，除非是 CHANGELOG 里记录的有意改变")
    void defaultScopeChangesOnlyWhereRecorded(String version) throws Exception {
        Map<String, String> released = fixture(version);
        Method dispatch = ShipmentDesk.class.getMethod("dispatch", String.class, Date.class);
        String current = ConcurrencyInterceptor.defaultScope(dispatch);

        assertThat(current).isEqualTo("com.ddk.compat.ShipmentDesk#dispatch(java.lang.String,java.util.Date)");
        assertThat(released.get("defaultScope")).isEqualTo(DELIBERATE_SCOPE_CHANGES.getOrDefault(version, current));
    }

    private static Map<String, String> fixture(String version) {
        String text = Releases.read(version, "concurrency-keys.json");
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD.matcher(text);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        assertThat(fields).containsOnlyKeys("defaultScope", "idempotentKey", "rateKey");
        return fields;
    }
}
