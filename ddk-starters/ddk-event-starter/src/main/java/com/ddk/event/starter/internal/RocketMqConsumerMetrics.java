package com.ddk.event.starter.internal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 每个消费组一个 gauge：{@code ddk.event.consume.failures{group}}，组里连续失败最多的队列失败了几次。
 * 顺序消费时它持续增长，说明有一条消息每次都失败，它所在的队列一直在等待。
 */
public class RocketMqConsumerMetrics implements SmartInitializingSingleton {

    private final ObjectProvider<MeterRegistry> registry;

    private final ObjectProvider<RocketMqEventConsumers> consumers;

    public RocketMqConsumerMetrics(ObjectProvider<MeterRegistry> registry, ObjectProvider<RocketMqEventConsumers> consumers) {
        this.registry = registry;
        this.consumers = consumers;
    }

    @Override
    public void afterSingletonsInstantiated() {
        MeterRegistry meters = registry.getIfAvailable();
        RocketMqEventConsumers rocketmq = consumers.getIfAvailable();
        if (meters == null || rocketmq == null) {
            return;
        }
        for (String group : rocketmq.groups()) {
            Gauge.builder("ddk.event.consume.failures", rocketmq, c -> c.consecutiveFailures(group))
                    .tag("group", group)
                    .description("Consecutive failures of the longest failing queue of the consumer group")
                    .strongReference(true)
                    .register(meters);
        }
    }
}
