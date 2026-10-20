package com.ddk.projection.starter.internal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;

/**
 * 输出到 Micrometer 的投影指标：
 * <ul>
 *     <li>{@code ddk.projection.refresh{projection, outcome=success|repeated|failed}}：每次刷新的耗时与次数</li>
 *     <li>{@code ddk.projection.pending{projection}}：待刷新的标记数量，包括等待重试的</li>
 *     <li>{@code ddk.projection.failing{projection}}：失败过、还在等待重试的标记数量</li>
 *     <li>{@code ddk.projection.oldest.age{projection}}：已到期的标记里等得最久的秒数，积压时持续上升</li>
 * </ul>
 * 三个 gauge 在每次采集时查询待刷新表，查询走主键和到期索引。{@code MeterRegistry} 通常由 actuator 在本 starter 之后注册，
 * 所以到 worker 启动或第一次记录时才去取。
 */
public class MicrometerRefreshMetrics implements RefreshMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;

    private volatile @Nullable MeterRegistry registry;

    public MicrometerRefreshMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public void bind(Collection<String> projections, PendingRefreshes pending) {
        MeterRegistry r = registry();
        if (r == null) {
            return;
        }
        for (String projection : projections) {
            Gauge.builder("ddk.projection.pending", pending, p -> p.count(projection))
                    .tag("projection", projection)
                    .description("Marks waiting to be refreshed, including retries")
                    .register(r);
            Gauge.builder("ddk.projection.failing", pending, p -> p.failing(projection))
                    .tag("projection", projection)
                    .description("Marks that failed at least once and wait for a retry")
                    .register(r);
            Gauge.builder("ddk.projection.oldest.age", pending, p -> p.oldestDueAge(projection).toMillis() / 1000.0)
                    .tag("projection", projection)
                    .baseUnit("seconds")
                    .description("How long the oldest due mark has been waiting")
                    .register(r);
        }
    }

    @Override
    public void refreshed(String projection, Outcome outcome, Duration took) {
        MeterRegistry r = registry();
        if (r != null) {
            Timer.builder("ddk.projection.refresh")
                    .tag("projection", projection)
                    .tag("outcome", outcome.name().toLowerCase(Locale.ROOT))
                    .register(r)
                    .record(took);
        }
    }

    private @Nullable MeterRegistry registry() {
        MeterRegistry r = registry;
        if (r == null) {
            r = registryProvider.getIfAvailable();
            registry = r;
        }
        return r;
    }
}
