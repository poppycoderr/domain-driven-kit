package com.ddk.event.starter.maintenance;

import com.ddk.core.domain.IntegrationEvent;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 用真实的 Spring Modulith JDBC 发布记录验证定时维护：投递失败的发布被重投，已完成的过了保留期被删除，已处理消息被清理，
 * 指标跟着状态变化。
 */
@DisplayName("事件定时维护（H2 + Modulith JDBC）")
@SpringBootTest(classes = EventMaintenanceIntegrationTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:event-maintenance;DB_CLOSE_DELAY=-1",
        "spring.modulith.events.jdbc.schema-initialization.enabled=true",
        "ddk.event.inbox.enabled=true",
        "ddk.event.maintenance.enabled=true",
        "ddk.event.maintenance.interval=200ms",
        "ddk.event.maintenance.resubmit-min-age=100ms",
        "ddk.event.maintenance.delete-completed-after=1s",
        "ddk.event.maintenance.inbox-retention=1s"
})
class EventMaintenanceIntegrationTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private FlakyChannel stockEvents;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private IdempotentConsumer inbox;

    @Test
    @DisplayName("失败的发布被重投并送达；已完成的过了保留期被删除；按状态的数量指标跟着变化")
    void failedPublicationsAreResubmittedAndCompletedOnesCleanedUp() {
        stockEvents.failures.set(1);
        transaction.executeWithoutResult(status -> publisher.publishEvent(new StockReserved("evt-1", 7L)));

        await().atMost(Duration.ofSeconds(5)).until(() -> failedGauge() == 1 || stockEvents.delivered.size() == 1);
        await().atMost(Duration.ofSeconds(10)).until(() -> stockEvents.delivered.size() == 1);
        assertThat(stockEvents.attempts.get()).as("the first send failed, maintenance sent it again").isEqualTo(2);
        await().atMost(Duration.ofSeconds(10)).until(() -> failedGauge() == 0);

        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Long.class) == 0);
    }

    @Test
    @DisplayName("已处理消息过了保留期被清理")
    void processedMessagesArePurged() {
        inbox.handle("stock", "msg-1", () -> { });
        assertThat(inbox.isProcessed("stock", "msg-1")).isTrue();

        await().atMost(Duration.ofSeconds(10)).until(() -> !inbox.isProcessed("stock", "msg-1"));
    }

    private double failedGauge() {
        return meters.get("ddk.event.publications").tag("status", "failed").gauge().value();
    }

    @IntegrationEvent(value = "stockEvents", id = "eventId")
    record StockReserved(
            String eventId,

            Long orderId
    ) {
    }

    static final class FlakyChannel implements MessageChannel {

        final AtomicInteger failures = new AtomicInteger();

        final AtomicInteger attempts = new AtomicInteger();

        final List<Message<?>> delivered = new CopyOnWriteArrayList<>();

        @Override
        public boolean send(Message<?> message, long timeout) {
            attempts.incrementAndGet();
            if (failures.getAndUpdate(left -> Math.max(left - 1, 0)) > 0) {
                throw new IllegalStateException("broker unavailable");
            }
            delivered.add(message);
            return true;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        FlakyChannel stockEvents() {
            return new FlakyChannel();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
