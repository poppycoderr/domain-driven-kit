package com.ddk.event.starter.integration;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.domain.Identifier;
import com.ddk.core.domain.IntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = IntegrationEventExternalizationTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:events;DB_CLOSE_DELAY=-1",
        "spring.modulith.events.jdbc.schema-initialization.enabled=true"
})
public class IntegrationEventExternalizationTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private RecordingChannel orderEvents;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        orderEvents.messages.clear();
        jdbc.update("DELETE FROM event_publication");
    }

    @Test
    void annotatedEventsAreRecordedInTheTransactionAndDeliveredAfterCommit() {
        transaction.executeWithoutResult(status -> publisher.publishEventsOf(Order.pay(OrderId.of(42L))));

        await().atMost(Duration.ofSeconds(5)).until(() -> orderEvents.messages.size() == 1);
        Message<?> message = orderEvents.messages.getFirst();
        assertThat(message.getPayload()).isEqualTo(new OrderPaid("evt-42", OrderId.of(42L), Instant.EPOCH));
        assertThat(message.getHeaders())
                .containsEntry("ddk-event-id", "evt-42")
                .containsEntry("ddk-event-type", "OrderPaid")
                .containsEntry("ddk-event-version", "1");
        assertThat(String.valueOf(message.getHeaders().get("springModulith_routingTarget"))).contains("orderEvents").contains("42");

        String serialized = jdbc.queryForObject("SELECT serialized_event FROM event_publication", String.class);
        assertThat(serialized).contains("\"orderId\":42");
    }

    @Test
    void rolledBackTransactionsDeliverNothing() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            publisher.publishEventsOf(Order.pay(OrderId.of(7L)));
            throw new IllegalStateException("payment rejected");
        })).hasMessage("payment rejected");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Long.class)).isZero();
        assertThat(orderEvents.messages).isEmpty();
    }

    @Test
    void eventsWithoutTheAnnotationStayInProcess() {
        transaction.executeWithoutResult(status -> publisher.publish(new OrderViewed(OrderId.of(1L), Instant.EPOCH)));

        assertThat(orderEvents.messages).isEmpty();
    }

    static final class OrderId extends Identifier<Long> {

        private OrderId(Long value) {
            super(value);
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }

    @IntegrationEvent(value = "orderEvents", key = "orderId", id = "eventId")
    record OrderPaid(
            String eventId,

            OrderId orderId,

            Instant occurredOn
    ) implements DomainEvent {
    }

    record OrderViewed(
            OrderId orderId,

            Instant occurredOn
    ) implements DomainEvent {
    }

    static final class Order extends AggregateRoot<OrderId> {

        static Order pay(OrderId id) {
            Order order = new Order();
            order.assignId(id);
            order.registerEvent(new OrderPaid("evt-" + id.value(), id, Instant.EPOCH));
            return order;
        }
    }

    static final class RecordingChannel implements MessageChannel {

        final List<Message<?>> messages = new CopyOnWriteArrayList<>();

        @Override
        public boolean send(Message<?> message, long timeout) {
            messages.add(message);
            return true;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class TestApplication {

        @Bean
        RecordingChannel orderEvents() {
            return new RecordingChannel();
        }
    }
}
