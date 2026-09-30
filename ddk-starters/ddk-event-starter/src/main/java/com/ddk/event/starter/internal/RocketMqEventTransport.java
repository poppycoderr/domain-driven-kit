package com.ddk.event.starter.internal;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.selector.SelectMessageQueueByHash;
import org.apache.rocketmq.common.message.Message;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.EvaluationContext;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.support.BrokerRouting;
import org.springframework.modulith.events.support.EventExternalizationTransport;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 把 Spring Modulith 外发的事件发送到 RocketMQ。
 * <p>
 * 投递目标按 RocketMQ 的惯例写成 {@code topic} 或 {@code topic:tag}。有 key 的事件按 key 哈希选队列，同一个聚合的事件落在同一个队列里，
 * 与 Kafka 按 key 分区一样保持顺序；key 同时写进消息的 keys，便于在控制台按业务标识查消息。事件的消息头写成用户属性。
 * 发送结果不是 {@link SendStatus#SEND_OK} 时按失败处理：发布记录保留为未完成，重投可能产生重复，由消费端幂等兜底。
 */
public final class RocketMqEventTransport implements EventExternalizationTransport {

    private static final MessageQueueSelector BY_KEY = new SelectMessageQueueByHash();

    private final DefaultMQProducer producer;

    private final EventExternalizationConfiguration configuration;

    private final JsonMapper mapper;

    private final EvaluationContext context;

    public RocketMqEventTransport(DefaultMQProducer producer, EventExternalizationConfiguration configuration, JsonMapper mapper,
            EvaluationContext context) {
        this.producer = producer;
        this.configuration = configuration;
        this.mapper = mapper;
        this.context = context;
    }

    @Override
    public CompletableFuture<?> externalize(Object payload, RoutingTarget target) {
        BrokerRouting routing = BrokerRouting.of(target, context);
        String key = routing.getKey(payload);
        Message message = message(routing.getTarget(payload), key, payload);

        CompletableFuture<SendResult> result = new CompletableFuture<>();
        SendCallback callback = new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                    result.complete(sendResult);
                } else {
                    result.completeExceptionally(new IllegalStateException(
                            "RocketMQ send to " + message.getTopic() + " finished with " + sendResult.getSendStatus()));
                }
            }

            @Override
            public void onException(Throwable failure) {
                result.completeExceptionally(failure);
            }
        };
        try {
            if (key == null) {
                producer.send(message, callback);
            } else {
                producer.send(message, BY_KEY, key, callback);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(e);
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    Message message(String destination, @Nullable String key, Object payload) {
        int separator = destination.indexOf(':');
        String topic = separator < 0 ? destination : destination.substring(0, separator);
        String tag = separator < 0 ? "" : destination.substring(separator + 1);

        Message message = new Message(topic, tag, body(payload));
        if (key != null) {
            message.setKeys(key);
        }
        configuration.getHeadersFor(payload).forEach((name, value) -> message.putUserProperty(name, String.valueOf(value)));
        return message;
    }

    private byte[] body(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        if (payload instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        return mapper.writeValueAsBytes(payload);
    }
}
