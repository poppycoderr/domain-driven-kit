package com.ddk.cache.starter.support;

import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JitteredTtlFunctionTest {

    private final JitteredTtlFunction ttl =
            new JitteredTtlFunction(Duration.ofMinutes(10), 0.1, Duration.ofMinutes(1));

    @Test
    void extendsTtlWithinJitterRangeWithoutShorteningIt() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(ttl.getTimeToLive("k", "v")).isBetween(Duration.ofMinutes(10), Duration.ofMinutes(11));
        }
    }

    @Test
    void nullValuesUseTheShortTtl() {
        assertThat(ttl.getTimeToLive("k", null)).isEqualTo(Duration.ofMinutes(1));
        assertThat(ttl.getTimeToLive("k", NullValue.INSTANCE)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void zeroTtlMeansNoExpiration() {
        JitteredTtlFunction persistent = new JitteredTtlFunction(Duration.ZERO, 0.1, Duration.ofMinutes(1));

        assertThat(persistent.getTimeToLive("k", "v")).isEqualTo(RedisCacheWriter.TtlFunction.NO_EXPIRATION);
    }

    @Test
    void rejectsJitterOutsideUnitRange() {
        assertThatThrownBy(() -> new JitteredTtlFunction(Duration.ofMinutes(1), 1.5, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
