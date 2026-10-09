package com.ddk.event.starter.internal;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 集成事件发送和处理的观测。
 * <p>
 * 用 Micrometer 的 Observation 而不是直接操作 Tracer：应用里有链路追踪时，发送方的观测会把链路上下文写进消息头，
 * 处理方的观测再从消息头里取出来接上，一次业务请求经过几个上下文仍然是同一条链路；同时每次发送和处理都计入指标。
 * 应用里没有 {@link ObservationRegistry} 时这些调用什么都不做。
 */
public final class EventObservations {

    public static final String PUBLISH = "ddk.event.publish";

    public static final String CONSUME = "ddk.event.consume";

    public static final EventObservations NOOP = new EventObservations(ObservationRegistry.NOOP);

    private static final String DESTINATION = "messaging.destination.name";

    private static final String GROUP = "messaging.consumer.group.name";

    private final ObservationRegistry registry;

    public EventObservations(ObservationRegistry registry) {
        this.registry = registry;
    }

    /**
     * 开始一次发送。链路上下文在这一步经 {@code headers} 写进消息头，所以要在消息真正发出之前调用；调用方在发送结束时停止返回的观测。
     */
    public Observation startPublish(String topic, BiConsumer<String, String> headers) {
        SenderContext<BiConsumer<String, String>> context = new SenderContext<>((carrier, name, value) -> {
            if (carrier != null) {
                carrier.accept(name, value);
            }
        });
        context.setCarrier(headers);
        context.setRemoteServiceName(topic);
        return Observation.createNotStarted(PUBLISH, () -> context, registry)
                .contextualName(topic + " publish")
                .lowCardinalityKeyValue(DESTINATION, topic)
                .start();
    }

    /**
     * 在一次处理的观测里执行 {@code action}，链路上下文取自消息头。
     */
    public <T> T consume(String group, String topic, Map<String, String> headers, Supplier<T> action) {
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get);
        context.setCarrier(headers);
        context.setRemoteServiceName(topic);
        return Observation.createNotStarted(CONSUME, () -> context, registry)
                .contextualName(topic + " process")
                .lowCardinalityKeyValue(DESTINATION, topic)
                .lowCardinalityKeyValue(GROUP, group)
                .observe(action);
    }
}
