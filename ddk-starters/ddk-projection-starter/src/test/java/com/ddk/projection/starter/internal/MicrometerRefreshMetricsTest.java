package com.ddk.projection.starter.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("投影的积压指标")
class MicrometerRefreshMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private PendingRefreshes pending;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:refresh-metrics-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1",
                "sa", "");
        pending = spy(new PendingRefreshes(new JdbcTemplate(dataSource), new DataSourceTransactionManager(dataSource), "pending",
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)));
        pending.prepare(true);
        pending.mark("catalog", "", "1");
        pending.mark("catalog", "", "2");
    }

    @Test
    @DisplayName("同一次采集的三个 gauge 共用一次查询")
    void oneQueryServesTheThreeGauges() {
        bind(MicrometerRefreshMetrics.SAMPLE_TTL);

        assertThat(gauge("ddk.projection.pending")).isEqualTo(2);
        assertThat(gauge("ddk.projection.failing")).isZero();
        assertThat(gauge("ddk.projection.oldest.age")).isZero();

        verify(pending, times(1)).backlog("catalog");
    }

    @Test
    @DisplayName("复用期过了就重新查询")
    void anExpiredSampleIsQueriedAgain() {
        bind(Duration.ZERO);

        gauge("ddk.projection.pending");
        gauge("ddk.projection.failing");

        verify(pending, times(2)).backlog("catalog");
    }

    private void bind(Duration ttl) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("registry", registry);
        new MicrometerRefreshMetrics(beans.getBeanProvider(MeterRegistry.class), ttl).bind(List.of("catalog"), pending);
    }

    private double gauge(String name) {
        return registry.get(name).tag("projection", "catalog").gauge().value();
    }
}
