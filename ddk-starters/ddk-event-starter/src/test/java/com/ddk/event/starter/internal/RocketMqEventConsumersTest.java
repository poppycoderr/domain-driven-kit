package com.ddk.event.starter.internal;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RocketMQ 订阅关系")
class RocketMqEventConsumersTest {

    record Declared(
            String group,

            String source
    ) implements IntegrationEventConsumer<String> {

        @Override
        public Class<String> payloadType() {
            return String.class;
        }

        @Override
        public void handle(ReceivedEvent<String> event) {
        }
    }

    @Test
    @DisplayName("每个消费组一份订阅，同一主题的标签合并；有消费方没写标签时订阅全部")
    void groupsTopicsAndTags() {
        Map<String, Map<String, Set<String>>> subscriptions = RocketMqEventConsumers.subscriptions(List.of(
                new Declared("inventory", "orders:placed"),
                new Declared("inventory", "orders:cancelled"),
                new Declared("inventory", "payments:paid"),
                new Declared("audit", "orders:placed"),
                new Declared("audit", "orders")));

        assertThat(subscriptions).containsOnlyKeys("inventory", "audit");
        assertThat(RocketMqEventConsumers.expression(subscriptions.get("inventory").get("orders"))).isEqualTo("placed || cancelled");
        assertThat(RocketMqEventConsumers.expression(subscriptions.get("inventory").get("payments"))).isEqualTo("paid");
        assertThat(RocketMqEventConsumers.expression(subscriptions.get("audit").get("orders"))).isEqualTo("*");
    }
}
