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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为声明的消费方创建 RocketMQ 消费者：每个消费组一个，订阅这个组关心的全部主题和标签。
 * <p>
 * 默认顺序消费。DDK 发消息时按 key 选队列，同一个聚合的事件在同一个队列里，顺序消费保证它们按发出的顺序被处理；
 * 处理失败时稍后重试当前队列，而不是跳过这条消息去处理后面的。不需要顺序时可以改成并发消费，失败的消息单独重投。
 * <p>
 * 在所有单例就绪之后才开始消费，应用关闭时先停止消费。
 * <p>
 * 顺序消费时，一条每次都失败的消息会让它所在的队列一直等待，RocketMQ 客户端对顺序消费的重试没有上限。所以这里按队列记下连续失败的次数：
 * 成功消费后清零，{@link #consecutiveFailures(String)} 给出一个消费组里失败最久的队列，失败达到 {@value #STUCK_AFTER} 次时记一条 ERROR。
 * <p>
 * 队列在重平衡时会转给别的实例，本实例再也收不到它的消息，也就等不到那次清零的成功。所以失败记录有时效：顺序消费默认每秒重试一次，
 * 超过 {@link #STALE_AFTER} 没有再失败的队列不再计入，否则这个数会停在最后的值上，直到重启。
 */
public class RocketMqEventConsumers implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RocketMqEventConsumers.class);

    static final int STUCK_AFTER = 10;

    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final IntegrationEventDispatcher dispatcher;

    private final String nameServer;

    private final boolean orderly;

    private final List<DefaultMQPushConsumer> consumers = new ArrayList<>();

    /**
     * 消费组 → 队列 → 连续失败次数和最近一次失败的时间。只记正在失败的队列。
     */
    private final Map<String, Map<String, Failure>> failing = new ConcurrentHashMap<>();

    private final Clock clock;

    private volatile boolean running;

    public RocketMqEventConsumers(IntegrationEventDispatcher dispatcher, String nameServer, boolean orderly) {
        this(dispatcher, nameServer, orderly, Clock.systemUTC());
    }

    RocketMqEventConsumers(IntegrationEventDispatcher dispatcher, String nameServer, boolean orderly, Clock clock) {
        this.dispatcher = dispatcher;
        this.nameServer = nameServer;
        this.orderly = orderly;
        this.clock = clock;
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

    boolean consume(String group, List<MessageExt> messages) {
        for (MessageExt message : messages) {
            try {
                Map<String, String> headers = message.getProperties() == null ? Map.of() : message.getProperties();
                dispatcher.dispatch(group, message.getTopic(), message.getTags(), headers, new String(message.getBody(), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                int failures = message.getReconsumeTimes() + 1;
                failing.computeIfAbsent(group, ignored -> new ConcurrentHashMap<>()).put(queue(message), new Failure(failures, clock.instant()));
                if (orderly && failures >= STUCK_AFTER) {
                    log.error("Consuming {}:{} [{}] in group {} has failed {} times in a row; its queue waits until the message succeeds. "
                            + "Fix the cause, or skip the message in the consumer", message.getTopic(), message.getTags(), message.getMsgId(),
                            group, failures, e);
                } else {
                    log.warn("Consuming {}:{} [{}] in group {} failed and will be retried", message.getTopic(), message.getTags(),
                            message.getMsgId(), group, e);
                }
                return false;
            }
        }
        Map<String, Failure> queues = failing.get(group);
        if (queues != null) {
            messages.forEach(message -> queues.remove(queue(message)));
        }
        return true;
    }

    /**
     * 一个消费组里连续失败最多的队列失败了几次；没有正在失败的队列时为 0。超过 {@link #STALE_AFTER} 没有再失败的队列不计入，并被清掉。
     */
    public int consecutiveFailures(String group) {
        Map<String, Failure> queues = failing.get(group);
        if (queues == null) {
            return 0;
        }
        Instant staleBefore = clock.instant().minus(STALE_AFTER);
        queues.values().removeIf(failure -> failure.at().isBefore(staleBefore));
        return queues.values().stream().mapToInt(Failure::count).max().orElse(0);
    }

    /**
     * 声明了消费方的消费组。
     */
    public Set<String> groups() {
        return subscriptions(dispatcher.consumers()).keySet();
    }

    private static String queue(MessageExt message) {
        return message.getTopic() + "@" + message.getBrokerName() + "#" + message.getQueueId();
    }

    private record Failure(
            int count,
            Instant at) {
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
