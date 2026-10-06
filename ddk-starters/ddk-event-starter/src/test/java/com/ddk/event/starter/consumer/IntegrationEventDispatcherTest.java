package com.ddk.event.starter.consumer;

import com.ddk.event.starter.inbox.IdempotentConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("集成事件分发")
class IntegrationEventDispatcherTest {

    private static final String JSON = "{\"orderId\":42,\"somethingNew\":true}";

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    record OrderPaid(
            Long orderId
    ) {
    }

    static class Recording implements IntegrationEventConsumer<OrderPaid> {

        final List<ReceivedEvent<OrderPaid>> received = new ArrayList<>();

        private final String group;

        private final String source;

        private final boolean idempotent;

        Recording(String group, String source, boolean idempotent) {
            this.group = group;
            this.source = source;
            this.idempotent = idempotent;
        }

        @Override
        public String group() {
            return group;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public Class<OrderPaid> payloadType() {
            return OrderPaid.class;
        }

        @Override
        public void handle(ReceivedEvent<OrderPaid> event) {
            received.add(event);
        }

        @Override
        public boolean idempotent() {
            return idempotent;
        }
    }

    @Test
    @DisplayName("按消费组、主题、标签匹配；只写主题的消费方接收全部标签")
    void matchesByGroupTopicAndTag() {
        Recording paid = new Recording("billing", "orders:paid", false);
        Recording everything = new Recording("billing", "orders", false);
        Recording otherGroup = new Recording("shipping", "orders:paid", false);
        Recording otherTag = new Recording("billing", "orders:cancelled", false);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(paid, everything, otherGroup, otherTag), jsonMapper, null);

        int handled = dispatcher.dispatch("billing", "orders", "paid", Map.of(), JSON);

        assertThat(handled).isEqualTo(2);
        assertThat(paid.received).hasSize(1);
        assertThat(everything.received).hasSize(1);
        assertThat(otherGroup.received).isEmpty();
        assertThat(otherTag.received).isEmpty();
        assertThat(dispatcher.dispatch("billing", "refunds", null, Map.of(), JSON)).isZero();
    }

    @Test
    @DisplayName("消息体按消费方的类型解析，契约头进入 ReceivedEvent")
    void decodesThePayloadAndHeaders() {
        Recording consumer = new Recording("billing", "orders:paid", false);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(consumer), jsonMapper, null);

        dispatcher.dispatch("billing", "orders", "paid",
                Map.of("ddk-event-id", "evt-1", "ddk-event-type", "order.paid", "ddk-event-version", "2"), JSON);
        dispatcher.dispatch("billing", "orders", "paid", Map.of("ddk-event-version", "not-a-number"), JSON);

        assertThat(consumer.received.get(0)).isEqualTo(new ReceivedEvent<>("evt-1", "order.paid", 2, new OrderPaid(42L)));
        assertThat(consumer.received.get(1)).isEqualTo(new ReceivedEvent<>(null, null, 0, new OrderPaid(42L)));
    }

    @Test
    @DisplayName("要求去重的消费方经 IdempotentConsumer 执行，重复的事件不再处理")
    void idempotentConsumersGoThroughTheInbox() {
        Recording consumer = new Recording("billing", "orders:paid", true);
        IdempotentConsumer inbox = mock(IdempotentConsumer.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(inbox).handle(eq("billing:Recording"), eq("evt-1"), any());
        when(inbox.handle(eq("billing:Recording"), eq("evt-2"), any())).thenReturn(false);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(consumer), jsonMapper, inbox);

        dispatcher.dispatch("billing", "orders", "paid", Map.of("ddk-event-id", "evt-1"), JSON);
        dispatcher.dispatch("billing", "orders", "paid", Map.of("ddk-event-id", "evt-2"), JSON);

        assertThat(consumer.received).extracting(ReceivedEvent::eventId).containsExactly("evt-1");
        assertThatThrownBy(() -> dispatcher.dispatch("billing", "orders", "paid", Map.of(), JSON))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no ddk-event-id header");
    }

    @Test
    @DisplayName("声明有误时在创建分发器时就报错")
    void invalidDeclarationsFailEarly() {
        assertThatThrownBy(() -> new IntegrationEventDispatcher(List.of(new Recording("billing", "orders", true)), jsonMapper, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("set ddk.event.inbox.enabled=true");
        assertThatThrownBy(() -> new IntegrationEventDispatcher(List.of(new Recording("billing", " ", false)), jsonMapper, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must declare a group and a source");
    }

    @Test
    @DisplayName("消费方抛出的异常原样传给调用方，由它决定重投")
    void failuresPropagate() {
        IntegrationEventConsumer<OrderPaid> failing = new Recording("billing", "orders", false) {

            @Override
            public void handle(ReceivedEvent<OrderPaid> event) {
                throw new IllegalStateException("database down");
            }
        };
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(failing), jsonMapper, null);

        assertThatThrownBy(() -> dispatcher.dispatch("billing", "orders", null, Map.of(), JSON)).hasMessage("database down");
    }
}
