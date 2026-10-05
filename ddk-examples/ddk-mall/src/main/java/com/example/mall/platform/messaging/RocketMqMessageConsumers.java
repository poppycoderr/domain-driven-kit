package com.example.mall.platform.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 从 RocketMQ 消费消息，compose profile 使用。
 * <p>
 * 每个「消费组 + 主题」一个消费者，订阅这个组在该主题上关心的全部标签。用顺序消费：DDK 发消息时按 key（订单号）选队列，
 * 同一个订单的消息在同一个队列里，顺序消费保证「已下单」先于「已取消」被处理。处理失败时稍后重试当前队列，而不是跳过。
 * <p>
 * 消息标识取自 DDK 写入的用户属性 {@code ddk-event-id}。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.messaging.transport", havingValue = "rocketmq")
public class RocketMqMessageConsumers implements SmartLifecycle {

    private static final String EVENT_ID_PROPERTY = "ddk-event-id";

    private final MessageDispatcher dispatcher;

    private final String nameServer;

    private final List<DefaultMQPushConsumer> consumers = new ArrayList<>();

    private volatile boolean running;

    public RocketMqMessageConsumers(MessageDispatcher dispatcher, @Value("${ddk.event.rocketmq.name-server}") String nameServer) {
        this.dispatcher = dispatcher;
        this.nameServer = nameServer;
    }

    @Override
    public void start() {
        Map<String, Map<String, List<MessageHandler<?>>>> byGroupAndTopic = dispatcher.handlers().stream()
                .collect(Collectors.groupingBy(MessageHandler::consumerGroup, Collectors.groupingBy(MessageHandler::topic)));
        byGroupAndTopic.forEach((group, byTopic) -> byTopic.forEach((topic, handlers) -> {
            String tags = handlers.stream().map(MessageHandler::tag).distinct().collect(Collectors.joining(" || "));
            consumers.add(subscribe(group, topic, tags));
        }));
        running = true;
    }

    private DefaultMQPushConsumer subscribe(String group, String topic, String tags) {
        // RocketMQ 要求同一个消费组的订阅完全一致，所以每个主题用一个独立的组名
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group + "-" + topic);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.registerMessageListener((MessageListenerOrderly) (messages, context) -> {
            for (MessageExt message : messages) {
                try {
                    dispatcher.dispatch(group, message.getTopic(), message.getTags(), message.getUserProperty(EVENT_ID_PROPERTY),
                            new String(message.getBody(), StandardCharsets.UTF_8));
                } catch (RuntimeException e) {
                    log.warn("Consuming {}:{} [{}] failed, will retry", message.getTopic(), message.getTags(), message.getMsgId(), e);
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }
            }
            return ConsumeOrderlyStatus.SUCCESS;
        });
        try {
            consumer.subscribe(topic, tags);
            consumer.start();
        } catch (MQClientException e) {
            throw new IllegalStateException("Failed to start the RocketMQ consumer of " + topic + " for " + group, e);
        }
        log.info("Consuming {} [{}] as {}", topic, tags, consumer.getConsumerGroup());
        return consumer;
    }

    @Override
    public void stop() {
        consumers.forEach(DefaultMQPushConsumer::shutdown);
        consumers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
