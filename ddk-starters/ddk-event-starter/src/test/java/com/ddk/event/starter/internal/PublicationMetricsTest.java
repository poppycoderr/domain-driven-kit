package com.ddk.event.starter.internal;

import com.ddk.event.starter.config.DdkEventProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicationMetricsTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private final EventPublicationRepository repository = mock(EventPublicationRepository.class);

    private final AtomicInteger queries = new AtomicInteger();

    private final AtomicBoolean failing = new AtomicBoolean();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T08:00:00Z"));

    PublicationMetricsTest() {
        when(repository.countByStatus(any())).thenAnswer(invocation -> {
            queries.incrementAndGet();
            if (failing.get()) {
                throw new IllegalStateException("database unavailable");
            }
            return switch (invocation.<EventPublication.Status>getArgument(0)) {
                case FAILED -> 2;
                case PUBLISHED -> 5;
                default -> 0;
            };
        });
    }

    @Test
    @DisplayName("采集只读最近一次采样：读多少次 gauge 都不查库，每轮采样按状态各查一次")
    void scrapesReadTheLastSample() {
        PublicationMetrics metrics = metrics(Duration.ofMinutes(1));

        assertThat(gauge("failed")).as("nothing sampled yet").isNaN();
        assertThat(queries).hasValue(0);

        metrics.sampleOnce();
        for (int i = 0; i < 100; i++) {
            assertThat(gauge("failed")).isEqualTo(2);
            assertThat(gauge("published")).isEqualTo(5);
            assertThat(gauge("processing")).isZero();
            assertThat(gauge("resubmitted")).isZero();
        }
        assertThat(queries).as("one count per watched status, none per scrape").hasValue(4);
    }

    @Test
    @DisplayName("采样失败时保留上一次的数，连续失败三轮后变成 NaN，恢复后重新有数")
    void keepsTheLastSampleUntilItIsStale() {
        PublicationMetrics metrics = metrics(Duration.ofMinutes(1));
        metrics.sampleOnce();

        failing.set(true);
        metrics.sampleOnce();
        metrics.sampleOnce();
        assertThat(gauge("failed")).as("two failures keep the last value").isEqualTo(2);
        metrics.sampleOnce();
        assertThat(gauge("failed")).as("three failures in a row").isNaN();

        failing.set(false);
        metrics.sampleOnce();
        assertThat(gauge("failed")).isEqualTo(2);
    }

    @Test
    @DisplayName("sample.age 是最近一次成功采样距今的秒数，失败的采样不刷新它")
    void reportsTheAgeOfTheLastSample() {
        PublicationMetrics metrics = metrics(Duration.ofMinutes(1));
        assertThat(meters.get("ddk.event.publications.sample.age").gauge().value()).isNaN();

        metrics.sampleOnce();
        clock.advance(Duration.ofSeconds(90));
        failing.set(true);
        metrics.sampleOnce();

        assertThat(meters.get("ddk.event.publications.sample.age").gauge().value()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("启动后由后台线程按间隔采样，停止后不再查库")
    void samplesInTheBackgroundWhileRunning() {
        PublicationMetrics metrics = metrics(Duration.ofSeconds(1));

        metrics.start();
        try {
            assertThat(metrics.isRunning()).isTrue();
            await().atMost(Duration.ofSeconds(5)).until(() -> gauge("failed") == 2);
        } finally {
            metrics.stop();
        }
        assertThat(metrics.isRunning()).isFalse();
        int afterStop = queries.get();
        await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(3)).until(() -> queries.get() == afterStop);
    }

    @Test
    @DisplayName("没有 MeterRegistry 或发布记录仓储时什么都不注册，也不启动采样线程")
    void staysIdleWithoutRegistryOrRepository() {
        StaticListableBeanFactory empty = new StaticListableBeanFactory();
        PublicationMetrics metrics = new PublicationMetrics(empty.getBeanProvider(MeterRegistry.class),
                empty.getBeanProvider(EventPublicationRepository.class), settings(Duration.ofMinutes(1)), clock);

        metrics.afterSingletonsInstantiated();
        metrics.start();

        assertThat(metrics.isRunning()).isFalse();
        assertThat(meters.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("采样间隔短于 1 秒时拒绝启动")
    void rejectsIntervalsShorterThanOneSecond() {
        assertThatThrownBy(() -> metrics(Duration.ofMillis(500)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddk.event.metrics.publications.interval");
    }

    private PublicationMetrics metrics(Duration interval) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("meterRegistry", meters);
        beans.addBean("eventPublicationRepository", repository);
        PublicationMetrics metrics = new PublicationMetrics(beans.getBeanProvider(MeterRegistry.class),
                beans.getBeanProvider(EventPublicationRepository.class), settings(interval), clock);
        metrics.afterSingletonsInstantiated();
        return metrics;
    }

    private static DdkEventProperties.Publications settings(Duration interval) {
        DdkEventProperties.Publications settings = new DdkEventProperties.Publications();
        settings.setInterval(interval);
        return settings;
    }

    private double gauge(String status) {
        return meters.get("ddk.event.publications").tag("status", status).gauge().value();
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
