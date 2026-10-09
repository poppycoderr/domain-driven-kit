package com.ddk.event.starter.consumer;

import com.ddk.core.domain.IntegrationEvent;
import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RocketMqContainer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 发布和消费都交给 DDK，中间是真实的 RocketMQ：事件经事件发布记录发出，由声明的消费方收到。
 * <p>
 * 提交后的投递是异步的，默认由线程池并发执行，先后提交的事件可能乱序发出。这里把线程池设成单线程，发出的顺序才等于提交的顺序；
 * 队列内的顺序和顺序消费由 RocketMQ 保证。
 * <p>
 * {@code @DirtiesContext} 让应用上下文在容器停止之前关闭，消费者才能正常退出。
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@DisplayName("从 RocketMQ 消费集成事件")
@SpringBootTest(classes = RocketMqEventConsumptionTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:rocketmq-consumption;DB_CLOSE_DELAY=-1",
        "spring.modulith.events.jdbc.schema-initialization.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.modulith.events.messaging.SpringMessagingEventExternalizerConfiguration",
        "ddk.event.inbox.enabled=true",
        "ddk.event.rocketmq.producer-group=consumption-test",
        "spring.task.execution.pool.core-size=1"
})
class RocketMqEventConsumptionTest {

    private static final String TOPIC = "shipment-events";

    private static final String SCAN_TOPIC = "parcel-scans";

    @Container
    static final RocketMqContainer ROCKETMQ = DdkContainers.rocketmq();

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private TrackingConsumer tracking;

    @Autowired
    private AuditConsumer audit;

    @Autowired
    private Tracer tracer;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private ScanConsumer scans;

    @DynamicPropertySource
    static void rocketmq(DynamicPropertyRegistry registry) {
        registry.add("ddk.event.rocketmq.name-server", ROCKETMQ::getNameServer);
    }

    @BeforeAll
    static void topic() {
        ROCKETMQ.createTopic(TOPIC, 4);
        ROCKETMQ.createTopic(SCAN_TOPIC, 1);
    }

    @Test
    @DisplayName("同一个 key 的事件按顺序到达；处理失败的消息被重投，后面的不会越过它；每个消费组各收一份")
    void eventsArriveInOrderAndFailuresAreRedelivered() {
        tracking.failuresLeft.set(1);
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ShipmentDispatched("evt-1", 77L, "出库")));
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ShipmentDispatched("evt-2", 77L, "运输中")));
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ShipmentDispatched("evt-3", 77L, "已签收")));

        await().atMost(Duration.ofSeconds(120)).until(() -> tracking.received.size() == 3 && audit.received.size() == 3);

        assertThat(tracking.received).extracting(event -> event.payload().status()).containsExactly("出库", "运输中", "已签收");
        assertThat(tracking.attempts).hasValue(4);
        assertThat(tracking.received.getFirst())
                .isEqualTo(new ReceivedEvent<>("evt-1", "shipment.dispatched", 1, new ShipmentView(77L, "出库")));
        assertThat(audit.received).extracting(ReceivedEvent::eventId).containsExactlyInAnyOrder("evt-1", "evt-2", "evt-3");
    }

    @Test
    @DisplayName("链路跟着消息走：消费方的处理过程接在发布方的那条链路上")
    void theTraceFollowsTheMessage() {
        // 和真实的请求一样，链路由一次观测开启（Web 请求的观测由 Spring 创建）
        String requestTrace = Observation.createNotStarted("http.server.requests", observationRegistry).observe(() -> {
            transaction.executeWithoutResult(status -> publisher.publishEvent(new ParcelScanned("evt-traced", 78L)));
            return Objects.requireNonNull(tracer.currentSpan()).context().traceId();
        });

        await().atMost(Duration.ofSeconds(120)).until(() -> scans.traces.containsKey("evt-traced"));

        assertThat(scans.traces.get("evt-traced")).isEqualTo(requestTrace);
    }

    @IntegrationEvent(value = TOPIC + ":dispatched", key = "shipmentId", id = "eventId", type = "shipment.dispatched")
    record ShipmentDispatched(
            String eventId,

            Long shipmentId,

            String status
    ) {
    }

    @IntegrationEvent(value = SCAN_TOPIC, key = "parcelId", id = "eventId")
    record ParcelScanned(
            String eventId,

            Long parcelId
    ) {
    }

    record ShipmentView(
            Long shipmentId,

            String status
    ) {
    }

    static class TrackingConsumer implements IntegrationEventConsumer<ShipmentView> {

        final List<ReceivedEvent<ShipmentView>> received = new CopyOnWriteArrayList<>();

        final AtomicInteger attempts = new AtomicInteger();

        final AtomicInteger failuresLeft = new AtomicInteger();

        @Override
        public String group() {
            return "tracking";
        }

        @Override
        public String source() {
            return TOPIC + ":dispatched";
        }

        @Override
        public Class<ShipmentView> payloadType() {
            return ShipmentView.class;
        }

        @Override
        public void handle(ReceivedEvent<ShipmentView> event) {
            attempts.incrementAndGet();
            if (failuresLeft.getAndDecrement() > 0) {
                throw new IllegalStateException("tracking store down");
            }
            received.add(event);
        }
    }

    /**
     * 记下处理每个事件时所在的链路。
     */
    static class ScanConsumer implements IntegrationEventConsumer<ParcelScanned> {

        final Map<String, String> traces = new ConcurrentHashMap<>();

        private final Tracer tracer;

        ScanConsumer(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public String group() {
            return "scans";
        }

        @Override
        public String source() {
            return SCAN_TOPIC;
        }

        @Override
        public Class<ParcelScanned> payloadType() {
            return ParcelScanned.class;
        }

        @Override
        public void handle(ReceivedEvent<ParcelScanned> event) {
            Span current = tracer.currentSpan();
            traces.put(event.payload().eventId(), current == null ? "" : current.context().traceId());
        }
    }

    static class AuditConsumer implements IntegrationEventConsumer<ShipmentView> {

        final List<ReceivedEvent<ShipmentView>> received = new CopyOnWriteArrayList<>();

        @Override
        public String group() {
            return "audit";
        }

        @Override
        public String source() {
            return TOPIC;
        }

        @Override
        public Class<ShipmentView> payloadType() {
            return ShipmentView.class;
        }

        @Override
        public boolean idempotent() {
            return true;
        }

        @Override
        public void handle(ReceivedEvent<ShipmentView> event) {
            received.add(event);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class TestApplication {

        @Bean
        TrackingConsumer trackingConsumer() {
            return new TrackingConsumer();
        }

        @Bean
        AuditConsumer auditConsumer() {
            return new AuditConsumer();
        }

        @Bean
        ScanConsumer scanConsumer(Tracer tracer) {
            return new ScanConsumer(tracer);
        }

        /**
         * 事务提交后的投递在线程池里进行。应用里这个 Bean 由链路 starter 提供，这里没有引入它，所以自己声明。
         */
        @Bean
        ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
            return new ContextPropagatingTaskDecorator();
        }
    }
}
