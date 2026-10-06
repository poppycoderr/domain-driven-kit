package com.ddk.event.starter.consumer;

import org.jspecify.annotations.Nullable;

/**
 * 收到的一条集成事件。
 *
 * @param eventId 事件标识，取自消息头 {@code ddk-event-id}；发布方没有声明 {@code @IntegrationEvent(id = ...)} 时为 {@code null}
 * @param type    契约名称，取自消息头 {@code ddk-event-type}
 * @param version 契约版本，取自消息头 {@code ddk-event-version}；消息头缺失时为 0
 * @param payload 按消费方声明的类型解析出的消息体
 */
public record ReceivedEvent<T>(
        @Nullable String eventId,

        @Nullable String type,

        int version,

        T payload
) {
}
