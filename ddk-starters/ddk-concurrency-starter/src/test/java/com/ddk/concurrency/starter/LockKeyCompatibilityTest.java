package com.ddk.concurrency.starter;

import com.ddk.compat.Releases;
import com.ddk.core.domain.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 聚合锁 key 的跨版本兼容：每个已发布版本的 {@code compat/<版本>/lock-key.txt} 是那个版本为订单 42 算出的锁名（见仓库的 {@code compat/generator}）。
 * 滚动发布时新旧实例必须锁同一个 key，否则同一个聚合会被两边同时修改。
 */
@DisplayName("与已发布版本的锁 key 兼容")
class LockKeyCompatibilityTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.ddk.compat.Releases#versions")
    @DisplayName("同一个聚合，当前版本算出的锁名与已发布版本相同")
    void namesTheSameLock(String version) {
        String expected = Releases.read(version, "lock-key.txt").strip();
        AggregateLocks locks = new AggregateLocks(mock(RedissonClient.class), "ddk:", Duration.ofSeconds(3), null);

        assertThat(locks.lockName("order", OrderId.of(42L))).isEqualTo(expected);
        assertThat(locks.lockName("order", 42L)).isEqualTo(expected);
    }

    static final class OrderId extends Identifier<Long> {

        private OrderId(Long value) {
            super(value);
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }
}
