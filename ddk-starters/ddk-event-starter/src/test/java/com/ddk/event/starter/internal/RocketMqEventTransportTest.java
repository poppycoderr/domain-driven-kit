package com.ddk.event.starter.internal;

import com.ddk.core.domain.IntegrationEvent;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("RocketMQ 事件投递")
class RocketMqEventTransportTest {

    private final IntegrationEventRouting routing = new IntegrationEventRouting();

    private final EventExternalizationConfiguration configuration = EventExternalizationConfiguration.externalizing()
            .selectAndRoute(IntegrationEvent.class, routing::route)
            .headers(routing::headers)
            .build();

    private final DefaultMQProducer producer = mock(DefaultMQProducer.class);

    private final RocketMqEventTransport transport =
            new RocketMqEventTransport(producer, configuration, JsonMapper.builder().build(), new StandardEvaluationContext());

    @IntegrationEvent(value = "orders:paid", key = "orderId", id = "eventId", type = "order.paid", version = 2)
    record OrderPaid(
            String eventId,

            Long orderId
    ) {
    }

    @Test
    @DisplayName("topic:tag 拆成主题和标签，key 写进 keys，契约头写成用户属性，消息体是 JSON")
    void mapsEventToMessage() {
        OrderPaid event = new OrderPaid("evt-1", 42L);

        Message message = transport.message("orders:paid", "42", event);

        assertThat(message.getTopic()).isEqualTo("orders");
        assertThat(message.getTags()).isEqualTo("paid");
        assertThat(message.getKeys()).isEqualTo("42");
        assertThat(message.getUserProperty("ddk-event-type")).isEqualTo("order.paid");
        assertThat(message.getUserProperty("ddk-event-version")).isEqualTo("2");
        assertThat(message.getUserProperty("ddk-event-id")).isEqualTo("evt-1");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"eventId\":\"evt-1\",\"orderId\":42}");
    }

    @Test
    @DisplayName("没有标签和 key 时只设置主题；字符串和字节数组原样发送")
    void keepsPlainTopicsAndRawPayloads() {
        Message text = transport.message("audit", null, "hello");
        Message bytes = transport.message("audit", null, new byte[] {1, 2});

        assertThat(text.getTopic()).isEqualTo("audit");
        assertThat(text.getTags()).isNull();
        assertThat(text.getKeys()).isNull();
        assertThat(text.getBody()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(bytes.getBody()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("有 key 的事件按 key 选队列，发送成功后完成")
    void sendsKeyedEventsThroughTheQueueSelector() throws Exception {
        answerWith(SendStatus.SEND_OK);

        CompletableFuture<?> result = transport.externalize(new OrderPaid("evt-1", 42L), RoutingTarget.forTarget("orders").andKey("42"));

        assertThat(result).isCompleted();
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture(), any(MessageQueueSelector.class), eq("42"), any(SendCallback.class));
        assertThat(message.getValue().getTopic()).isEqualTo("orders");
    }

    @Test
    @DisplayName("没有 key 的事件不指定队列")
    void sendsUnkeyedEventsWithoutSelector() throws Exception {
        CompletableFuture<?> result = transport.externalize("hello", RoutingTarget.forTarget("audit").withoutKey());

        verify(producer).send(any(Message.class), any(SendCallback.class));
        assertThat(result).isNotDone();
    }

    @Test
    @DisplayName("broker 没有确认成功时按失败处理，发布记录会保留以便重投")
    void failsWhenBrokerDoesNotConfirm() throws Exception {
        answerWith(SendStatus.FLUSH_SLAVE_TIMEOUT);

        CompletableFuture<?> result = transport.externalize(new OrderPaid("evt-1", 42L), RoutingTarget.forTarget("orders").andKey("42"));

        assertThat(result).isCompletedExceptionally();
        assertThat(result.exceptionNow()).hasMessageContaining("FLUSH_SLAVE_TIMEOUT");
    }

    @Test
    @DisplayName("发送时的异常与回调里的异常都让结果失败")
    void propagatesSendFailures() throws Exception {
        doThrow(new MQClientException("no route", null))
                .when(producer).send(any(Message.class), any(MessageQueueSelector.class), any(), any(SendCallback.class));
        CompletableFuture<?> thrown = transport.externalize(new OrderPaid("evt-1", 42L), RoutingTarget.forTarget("orders").andKey("42"));
        assertThat(thrown.exceptionNow()).isInstanceOf(MQClientException.class);

        doAnswer(invocation -> {
            invocation.<SendCallback>getArgument(1).onException(new IllegalStateException("broker down"));
            return null;
        }).when(producer).send(any(Message.class), any(SendCallback.class));
        CompletableFuture<?> callback = transport.externalize("hello", RoutingTarget.forTarget("audit").withoutKey());
        assertThat(callback.exceptionNow()).hasMessage("broker down");
    }

    private void answerWith(SendStatus status) throws Exception {
        SendResult result = new SendResult();
        result.setSendStatus(status);
        doAnswer(invocation -> {
            invocation.<SendCallback>getArgument(3).onSuccess(result);
            return null;
        }).when(producer).send(any(Message.class), any(MessageQueueSelector.class), any(), any(SendCallback.class));
    }
}
