package com.ddk.event.starter.consumer;

import com.ddk.core.context.OperatorContext;
import com.ddk.core.domain.IntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 进程内转发：发布方和消费方在同一个应用里，不需要消息中间件。
 */
@DisplayName("进程内转发集成事件")
@SpringBootTest(classes = LocalEventDeliveryTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:local-delivery;DB_CLOSE_DELAY=-1",
        "spring.modulith.events.jdbc.schema-initialization.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.modulith.events.messaging.SpringMessagingEventExternalizerConfiguration",
        "ddk.event.local-delivery.enabled=true",
        "ddk.event.inbox.enabled=true"
})
class LocalEventDeliveryTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private InvoiceConsumer invoices;

    @Autowired
    private RefundConsumer refunds;

    @BeforeEach
    void reset() {
        invoices.received.clear();
        invoices.failNext = false;
    }

    @Test
    @DisplayName("事务提交后按顺序送达，消费方拿到的是自己定义的消息体和契约头")
    void committedEventsReachTheConsumerInOrder() {
        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new ParcelShipped("evt-1", 1L, "SF"));
            publisher.publishEvent(new ParcelShipped("evt-2", 2L, "JD"));
            publisher.publishEvent("not an integration event");
        });

        await().atMost(Duration.ofSeconds(5)).until(() -> invoices.received.size() == 2);
        assertThat(invoices.received).extracting(ReceivedEvent::eventId).containsExactly("evt-1", "evt-2");
        assertThat(invoices.received.getFirst()).isEqualTo(new ReceivedEvent<>("evt-1", "parcel.shipped", 3, new ParcelView(1L)));
    }

    @Test
    @DisplayName("事务回滚时什么都不送")
    void rolledBackEventsAreNotDelivered() throws Exception {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new ParcelShipped("evt-3", 3L, "SF"));
            throw new IllegalStateException("rolled back");
        })).hasMessage("rolled back");
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ParcelShipped("evt-4", 4L, "SF")));

        await().atMost(Duration.ofSeconds(5)).until(() -> invoices.received.size() == 1);
        assertThat(invoices.received).extracting(ReceivedEvent::eventId).containsExactly("evt-4");
    }

    @Test
    @DisplayName("同一个事件标识只处理一次；处理失败只记日志，后面的事件照常送达")
    void duplicatesAreSkippedAndFailuresDoNotBlockLaterEvents() {
        invoices.failNext = true;
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ParcelShipped("evt-5", 5L, "SF")));
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ParcelShipped("evt-6", 6L, "SF")));
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ParcelShipped("evt-6", 6L, "SF")));
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ParcelShipped("evt-7", 7L, "SF")));

        await().atMost(Duration.ofSeconds(5)).until(() -> invoices.received.stream().anyMatch(event -> "evt-7".equals(event.eventId())));
        assertThat(invoices.received).extracting(ReceivedEvent::eventId).containsExactly("evt-6", "evt-7");
    }

    @Test
    @DisplayName("租户随事件数据投递：发布时线程上没有租户也不影响，消费方在事件的租户里处理")
    void theTenantTravelsWithTheEvent() {
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ParcelReturned("evt-r1", 8L, "tenant-a")));

        await().atMost(Duration.ofSeconds(5)).until(() -> !refunds.operators.isEmpty());
        assertThat(refunds.operators).containsExactly("ddk-event/tenant-a");
    }

    @IntegrationEvent(value = "parcels:returned", id = "eventId", tenant = "tenantId")
    record ParcelReturned(
            String eventId,

            Long parcelId,

            String tenantId
    ) {
    }

    static class RefundConsumer implements IntegrationEventConsumer<ParcelView> {

        final List<String> operators = new CopyOnWriteArrayList<>();

        @Override
        public String group() {
            return "refunds";
        }

        @Override
        public String source() {
            return "parcels:returned";
        }

        @Override
        public Class<ParcelView> payloadType() {
            return ParcelView.class;
        }

        @Override
        public boolean idempotent() {
            return true;
        }

        @Override
        public boolean runInEventTenant() {
            return true;
        }

        @Override
        public void handle(ReceivedEvent<ParcelView> event) {
            operators.add(OperatorContext.current().map(operator -> operator.id() + "/" + operator.tenantId()).orElse("none"));
        }
    }

    @IntegrationEvent(value = "parcels:shipped", key = "parcelId", id = "eventId", type = "parcel.shipped", version = 3)
    record ParcelShipped(
            String eventId,

            Long parcelId,

            String carrier
    ) {
    }

    record ParcelView(
            Long parcelId
    ) {
    }

    static class InvoiceConsumer implements IntegrationEventConsumer<ParcelView> {

        final List<ReceivedEvent<ParcelView>> received = new CopyOnWriteArrayList<>();

        volatile boolean failNext;

        @Override
        public String group() {
            return "invoicing";
        }

        @Override
        public String source() {
            return "parcels:shipped";
        }

        @Override
        public Class<ParcelView> payloadType() {
            return ParcelView.class;
        }

        @Override
        public boolean idempotent() {
            return true;
        }

        @Override
        public void handle(ReceivedEvent<ParcelView> event) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("invoice service down");
            }
            received.add(event);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class TestApplication {

        @Bean
        InvoiceConsumer invoiceConsumer() {
            return new InvoiceConsumer();
        }

        @Bean
        RefundConsumer refundConsumer() {
            return new RefundConsumer();
        }
    }
}
