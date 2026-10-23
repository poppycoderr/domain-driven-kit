package com.ddk.projection.starter.internal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 输出到 Micrometer 的投影指标：
 * <ul>
 *     <li>{@code ddk.projection.refresh{projection, outcome=success|repeated|failed}}：每次刷新的耗时与次数</li>
 *     <li>{@code ddk.projection.pending{projection}}：待刷新的标记数量，包括等待重试的</li>
 *     <li>{@code ddk.projection.failing{projection}}：失败过、还在等待重试的标记数量</li>
 *     <li>{@code ddk.projection.oldest.age{projection}}：已到期的标记里等得最久的秒数，积压时持续上升</li>
 * </ul>
 * 同一个读模型的三个 gauge 共用一次查询：采集时只扫描一次待刷新表，结果在 {@link #SAMPLE_TTL} 内复用，同一次采集的三个值彼此一致。
 * 积压很大时这次扫描并不便宜（MySQL 上一百万条标记约半秒），采集间隔不要太短。{@code MeterRegistry} 通常由 actuator 在本 starter
 * 之后注册，所以到 worker 启动或第一次记录时才去取。
 */
public class MicrometerRefreshMetrics implements RefreshMetrics {

    static final Duration SAMPLE_TTL = Duration.ofSeconds(1);

    private final ObjectProvider<MeterRegistry> registryProvider;

    private final Duration sampleTtl;

    private volatile @Nullable MeterRegistry registry;

    public MicrometerRefreshMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider, SAMPLE_TTL);
    }

    MicrometerRefreshMetrics(ObjectProvider<MeterRegistry> registryProvider, Duration sampleTtl) {
        this.registryProvider = registryProvider;
        this.sampleTtl = sampleTtl;
    }

    @Override
    public void bind(Collection<String> projections, PendingRefreshes pending) {
        MeterRegistry r = registry();
        if (r == null) {
            return;
        }
        for (String projection : projections) {
            Sampled backlog = new Sampled(() -> pending.backlog(projection), sampleTtl);
            Gauge.builder("ddk.projection.pending", backlog, b -> b.get().pending())
                    .tag("projection", projection)
                    .description("Marks waiting to be refreshed, including retries")
                    .register(r);
            Gauge.builder("ddk.projection.failing", backlog, b -> b.get().failing())
                    .tag("projection", projection)
                    .description("Marks that failed at least once and wait for a retry")
                    .register(r);
            Gauge.builder("ddk.projection.oldest.age", backlog, b -> b.get().oldestDueAge().toMillis() / 1000.0)
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

    /**
     * 最近一次查询的积压，过期后再查。两次采集碰巧同时过期时会各查一次，结果都对，所以不加锁。
     */
    private static final class Sampled {

        private final Supplier<PendingRefreshes.Backlog> query;

        private final long ttlNanos;

        private volatile @Nullable Sample last;

        Sampled(Supplier<PendingRefreshes.Backlog> query, Duration ttl) {
            this.query = query;
            this.ttlNanos = ttl.toNanos();
        }

        PendingRefreshes.Backlog get() {
            Sample sample = last;
            long now = System.nanoTime();
            if (sample == null || now - sample.at() >= ttlNanos) {
                sample = new Sample(now, query.get());
                last = sample;
            }
            return sample.backlog();
        }
    }

    private record Sample(
            long at,
            PendingRefreshes.Backlog backlog
    ) {
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
