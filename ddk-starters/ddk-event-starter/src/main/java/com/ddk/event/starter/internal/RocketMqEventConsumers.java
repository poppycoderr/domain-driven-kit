package com.ddk.event.starter.internal;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为声明的消费方创建 RocketMQ 消费者：每个消费组一个，订阅这个组关心的全部主题和标签。
 * <p>
 * 默认顺序消费。DDK 发消息时按 key 选队列，同一个聚合的事件在同一个队列里，顺序消费保证它们按发出的顺序被处理；
 * 处理失败时稍后重试当前队列，而不是跳过这条消息去处理后面的。不需要顺序时可以改成并发消费，失败的消息单独重投。
 * <p>
 * 在所有单例就绪之后才开始消费，应用关闭时先停止消费。
 */
public class RocketMqEventConsumers implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RocketMqEventConsumers.class);

    private final IntegrationEventDispatcher dispatcher;

    private final String nameServer;

    private final boolean orderly;

    private final List<DefaultMQPushConsumer> consumers = new ArrayList<>();

    private volatile boolean running;

    public RocketMqEventConsumers(IntegrationEventDispatcher dispatcher, String nameServer, boolean orderly) {
        this.dispatcher = dispatcher;
        this.nameServer = nameServer;
        this.orderly = orderly;
    }

    @Override
    public void start() {
        subscriptions(dispatcher.consumers()).forEach((group, topics) -> consumers.add(subscribe(group, topics)));
        running = true;
    }

    /**
     * 消费组 → 主题 → 标签。某个主题上有消费方没有指定标签时，订阅该主题的全部标签。
     */
    static Map<String, Map<String, Set<String>>> subscriptions(List<IntegrationEventConsumer<?>> declared) {
        Map<String, Map<String, Set<String>>> subscriptions = new LinkedHashMap<>();
        for (IntegrationEventConsumer<?> consumer : declared) {
            String[] source = consumer.source().split(":", 2);
            subscriptions.computeIfAbsent(consumer.group(), group -> new LinkedHashMap<>())
                    .computeIfAbsent(source[0], topic -> new LinkedHashSet<>())
                    .add(source.length > 1 ? source[1] : "*");
        }
        return subscriptions;
    }

    static String expression(Set<String> tags) {
        return tags.contains("*") ? "*" : String.join(" || ", tags);
    }

    private DefaultMQPushConsumer subscribe(String group, Map<String, Set<String>> topics) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        if (orderly) {
            consumer.registerMessageListener((MessageListenerOrderly) (messages, context) ->
                    consume(group, messages) ? ConsumeOrderlyStatus.SUCCESS : ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT);
        } else {
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) ->
                    consume(group, messages) ? ConsumeConcurrentlyStatus.CONSUME_SUCCESS : ConsumeConcurrentlyStatus.RECONSUME_LATER);
        }
        try {
            for (Map.Entry<String, Set<String>> topic : topics.entrySet()) {
                consumer.subscribe(topic.getKey(), expression(topic.getValue()));
            }
            consumer.start();
        } catch (MQClientException e) {
            throw new IllegalStateException("Failed to start the RocketMQ consumer of group " + group, e);
        }
        log.info("Consuming {} as group {} ({})", topics, group, orderly ? "orderly" : "concurrently");
        return consumer;
    }

    private boolean consume(String group, List<MessageExt> messages) {
        for (MessageExt message : messages) {
            try {
                Map<String, String> headers = message.getProperties() == null ? Map.of() : message.getProperties();
                dispatcher.dispatch(group, message.getTopic(), message.getTags(), headers, new String(message.getBody(), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                log.warn("Consuming {}:{} [{}] in group {} failed and will be retried", message.getTopic(), message.getTags(),
                        message.getMsgId(), group, e);
                return false;
            }
        }
        return true;
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
