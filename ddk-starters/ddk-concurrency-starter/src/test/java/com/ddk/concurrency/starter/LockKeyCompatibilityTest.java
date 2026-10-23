package com.ddk.concurrency.starter;

import com.ddk.core.domain.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 聚合锁 key 的跨版本兼容：{@code compat/0.10.0/lock-key.txt} 是 DDK 0.10.0 为订单 42 算出的锁名（见仓库的 {@code compat/generator}）。
 * 滚动发布时新旧实例必须锁同一个 key，否则同一个聚合会被两边同时修改。
 */
@DisplayName("与 0.10.0 的锁 key 兼容")
class LockKeyCompatibilityTest {

    @Test
    @DisplayName("同一个聚合，当前版本算出的锁名与 0.10.0 相同")
    void namesTheSameLock() throws IOException {
        String expected = new ClassPathResource("compat/0.10.0/lock-key.txt").getContentAsString(StandardCharsets.UTF_8).strip();
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
