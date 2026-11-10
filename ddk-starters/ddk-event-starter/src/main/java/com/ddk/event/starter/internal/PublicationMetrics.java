package com.ddk.event.starter.internal;

import com.ddk.event.starter.config.DdkEventProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.SmartLifecycle;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 发布记录按状态的数量：{@code ddk.event.publications{status=published|processing|failed|resubmitted}}，
 * 以及最近一次采样距今的秒数 {@code ddk.event.publications.sample.age}。已完成的不统计，它们只会越来越多。
 * <p>
 * Modulith 的发布记录表在状态列上没有索引，按状态计数要扫全表。所以计数不在采集时做，而是由一个后台线程按
 * {@code ddk.event.metrics.publications.interval} 定期采样，gauge 只读最近一次的结果：查询数与采集频率无关，
 * 数值最多晚一个间隔。同一时刻最多只有一轮采样（固定延迟调度），慢查询不会堆积。
 * <p>
 * 一轮里任一次计数失败就保留上一次的结果；连续失败 {@value #STALE_AFTER_FAILURES} 轮后 gauge 变为 NaN，
 * 不让一个过时的数字看起来像正常值。
 */
public class PublicationMetrics implements SmartInitializingSingleton, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PublicationMetrics.class);

    static final int STALE_AFTER_FAILURES = 3;

    private static final Duration MAX_FIRST_DELAY = Duration.ofSeconds(5);

    private static final List<EventPublication.Status> WATCHED = List.of(EventPublication.Status.PUBLISHED, EventPublication.Status.PROCESSING,
            EventPublication.Status.FAILED, EventPublication.Status.RESUBMITTED);

    private final ObjectProvider<MeterRegistry> registry;

    private final ObjectProvider<EventPublicationRepository> repository;

    private final Duration interval;

    private final Clock clock;

    private @Nullable EventPublicationRepository publications;

    private volatile @Nullable Sample last;

    private volatile int failures;

    private @Nullable ScheduledExecutorService executor;

    public PublicationMetrics(ObjectProvider<MeterRegistry> registry, ObjectProvider<EventPublicationRepository> repository,
            DdkEventProperties.Publications settings) {
        this(registry, repository, settings, Clock.systemUTC());
    }

    PublicationMetrics(ObjectProvider<MeterRegistry> registry, ObjectProvider<EventPublicationRepository> repository,
            DdkEventProperties.Publications settings, Clock clock) {
        settings.validate();
        this.registry = registry;
        this.repository = repository;
        this.interval = settings.getInterval();
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        MeterRegistry meters = registry.getIfAvailable();
        EventPublicationRepository found = repository.getIfAvailable();
        if (meters == null || found == null) {
            return;
        }
        publications = found;
        for (EventPublication.Status status : WATCHED) {
            Gauge.builder("ddk.event.publications", this, metrics -> metrics.count(status))
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .description("Event publications in this status, as of the last sample")
                    .strongReference(true)
                    .register(meters);
        }
        Gauge.builder("ddk.event.publications.sample.age", this, PublicationMetrics::sampleAgeSeconds)
                .description("Seconds since the publication counts were last sampled")
                .baseUnit("seconds")
                .strongReference(true)
                .register(meters);
    }

    /**
     * 采样一轮：按状态各计数一次。任一次失败就保留上一次的结果。
     */
    void sampleOnce() {
        EventPublicationRepository source = publications;
        if (source == null) {
            return;
        }
        try {
            Map<EventPublication.Status, Integer> counts = new EnumMap<>(EventPublication.Status.class);
            for (EventPublication.Status status : WATCHED) {
                counts.put(status, source.countByStatus(status));
            }
            last = new Sample(counts, clock.instant());
            if (failures > 0) {
                log.info("Counting event publications works again after {} failed samples", failures);
            }
            failures = 0;
        } catch (RuntimeException e) {
            int failed = ++failures;
            if (failed == 1 || failed == STALE_AFTER_FAILURES) {
                log.warn("Could not count event publications ({} failed samples in a row){}", failed,
                        failed >= STALE_AFTER_FAILURES ? "; ddk.event.publications reports NaN until a sample succeeds" : "", e);
            } else {
                log.debug("Could not count event publications ({} failed samples in a row)", failed, e);
            }
        }
    }

    double count(EventPublication.Status status) {
        Sample sample = last;
        if (sample == null || failures >= STALE_AFTER_FAILURES) {
            return Double.NaN;
        }
        Integer count = sample.counts().get(status);
        return count == null ? Double.NaN : count;
    }

    double sampleAgeSeconds() {
        Sample sample = last;
        return sample == null ? Double.NaN : Duration.between(sample.at(), clock.instant()).toMillis() / 1000.0;
    }

    @Override
    public void start() {
        if (publications == null) {
            return;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ddk-publication-metrics");
            thread.setDaemon(true);
            return thread;
        });
        long first = Math.min(interval.toMillis(), MAX_FIRST_DELAY.toMillis());
        scheduler.scheduleWithFixedDelay(this::sampleSafely, first, interval.toMillis(), TimeUnit.MILLISECONDS);
        executor = scheduler;
    }

    private void sampleSafely() {
        try {
            sampleOnce();
        } catch (RuntimeException e) {
            log.warn("Sampling event publications failed", e);
        }
    }

    @Override
    public void stop() {
        ScheduledExecutorService scheduler = executor;
        if (scheduler != null) {
            scheduler.shutdownNow();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }

    private record Sample(
            Map<EventPublication.Status, Integer> counts,
            Instant at) {
    }
}
