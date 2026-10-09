package com.ddk.event.starter.internal;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.consumer.ReceivedEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("集成事件的观测与链路传递")
class EventObservationsTest {

    private static final String TRACE_HEADER = "traceparent";

    private final TestObservationRegistry registry = TestObservationRegistry.create();

    private final AtomicReference<String> extracted = new AtomicReference<>();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    EventObservationsTest() {
        registry.observationConfig().observationHandler(new FakeTracing());
    }

    /**
     * 做链路追踪的处理器做的两件事：发送时把上下文写进消息头，接收时从消息头里读出来。
     */
    private final class FakeTracing implements ObservationHandler<Observation.Context> {

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof SenderContext<?> || context instanceof ReceiverContext<?>;
        }

        @Override
        public void onStart(Observation.Context context) {
            if (context instanceof SenderContext<?> sender) {
                inject(sender);
            } else if (context instanceof ReceiverContext<?> receiver) {
                extracted.set(extract(receiver));
            }
        }

        private <C> void inject(SenderContext<C> sender) {
            sender.getSetter().set(sender.getCarrier(), TRACE_HEADER, "trace-1");
        }

        private <C> String extract(ReceiverContext<C> receiver) {
            return receiver.getGetter().get(receiver.getCarrier(), TRACE_HEADER);
        }
    }

    record Paid(
            Long orderId
    ) {
    }

    @Test
    @DisplayName("发送到 RocketMQ：链路上下文写成用户属性，broker 确认后观测结束")
    void publishingToRocketMqCarriesTheTraceAndStopsOnConfirmation() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        answer(producer, callback -> callback.onSuccess(result(SendStatus.SEND_OK)));

        transport(producer).externalize("hello", RoutingTarget.forTarget("orders:paid").withoutKey());

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture(), any(SendCallback.class));
        assertThat(message.getValue().getUserProperty(TRACE_HEADER)).isEqualTo("trace-1");
        assertThat(registry).hasObservationWithNameEqualTo(EventObservations.PUBLISH).that()
                .hasContextualNameEqualTo("orders publish")
                .hasLowCardinalityKeyValue("messaging.destination.name", "orders")
                .hasBeenStopped()
                .doesNotHaveError();
    }

    @Test
    @DisplayName("发送失败记在观测上")
    void aFailedSendIsRecordedOnTheObservation() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        answer(producer, callback -> callback.onException(new IllegalStateException("broker down")));

        transport(producer).externalize("hello", RoutingTarget.forTarget("orders").withoutKey());

        assertThat(registry).hasObservationWithNameEqualTo(EventObservations.PUBLISH).that()
                .hasBeenStopped()
                .hasError();
    }

    @Test
    @DisplayName("处理消息：从消息头取出链路上下文，处理过程在观测之内，失败记在观测上")
    void consumingContinuesTheTraceFromTheHeaders() {
        AtomicReference<Observation> during = new AtomicReference<>();
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(
                List.of(consumer("billing", "orders:paid", () -> during.set(registry.getCurrentObservation()))), jsonMapper, null, registry);

        int handled = dispatcher.dispatch("billing", "orders", "paid", Map.of(TRACE_HEADER, "trace-9"), "{\"orderId\":42}");

        assertThat(handled).isEqualTo(1);
        assertThat(extracted).hasValue("trace-9");
        assertThat(during.get()).as("the handler runs inside the observation").isNotNull();
        assertThat(registry).hasObservationWithNameEqualTo(EventObservations.CONSUME).that()
                .hasContextualNameEqualTo("orders process")
                .hasLowCardinalityKeyValue("messaging.destination.name", "orders")
                .hasLowCardinalityKeyValue("messaging.consumer.group.name", "billing")
                .hasBeenStopped()
                .doesNotHaveError();
    }

    @Test
    @DisplayName("处理失败记在观测上并照常抛出；没有消费方订阅的消息不产生观测")
    void failuresAreRecordedAndUnmatchedMessagesAreNotObserved() {
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(consumer("billing", "orders:paid", () -> {
            throw new IllegalStateException("ledger down");
        })), jsonMapper, null, registry);

        assertThat(dispatcher.dispatch("billing", "orders", "refunded", Map.of(), "{}")).isZero();
        assertThat(registry).doesNotHaveAnyObservation();

        assertThatThrownBy(() -> dispatcher.dispatch("billing", "orders", "paid", Map.of(), "{\"orderId\":42}")).hasMessage("ledger down");
        assertThat(registry).hasObservationWithNameEqualTo(EventObservations.CONSUME).that().hasBeenStopped().hasError();
    }

    @Test
    @DisplayName("没有观测登记处时什么都不做")
    void nothingHappensWithoutARegistry() {
        Map<String, String> headers = new HashMap<>();

        EventObservations.NOOP.startPublish("orders", headers::put).stop();

        assertThat(headers).isEmpty();
        assertThat(EventObservations.NOOP.consume("billing", "orders", headers, () -> "done")).isEqualTo("done");
    }

    private RocketMqEventTransport transport(DefaultMQProducer producer) {
        EventExternalizationConfiguration configuration = EventExternalizationConfiguration.externalizing().select(event -> true).build();
        return new RocketMqEventTransport(producer, configuration, jsonMapper, new StandardEvaluationContext(), new EventObservations(registry));
    }

    private static void answer(DefaultMQProducer producer, java.util.function.Consumer<SendCallback> reply) throws Exception {
        doAnswer(invocation -> {
            reply.accept(invocation.getArgument(1));
            return null;
        }).when(producer).send(any(Message.class), any(SendCallback.class));
    }

    private static SendResult result(SendStatus status) {
        SendResult result = new SendResult();
        result.setSendStatus(status);
        return result;
    }

    private static IntegrationEventConsumer<Paid> consumer(String group, String source, Runnable onHandle) {
        return new IntegrationEventConsumer<>() {

            @Override
            public String group() {
                return group;
            }

            @Override
            public String source() {
                return source;
            }

            @Override
            public Class<Paid> payloadType() {
                return Paid.class;
            }

            @Override
            public void handle(ReceivedEvent<Paid> event) {
                onHandle.run();
            }
        };
    }
}
