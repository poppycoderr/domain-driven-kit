package com.ddk.event.starter.consumer;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 一条在消费组里失败次数达到 {@code ddk.event.rocketmq.consumer.max-attempts} 的消息，原样交给 {@link UndeliverableEventHandler}。
 * <p>
 * 消息体是收到的 JSON，没有按消费方的类型解析：失败的原因可能正是解析不了。
 *
 * @param group       消费组
 * @param topic       主题
 * @param tag         标签，消息没有标签时为 {@code null}
 * @param messageId   消息中间件给的消息标识
 * @param headers     消息头，包括 {@code ddk-event-id}、{@code ddk-event-type} 等
 * @param body        消息体
 * @param attempts    这条消息在这个消费组里一共失败了几次
 * @param lastFailure 最近一次失败的异常
 */
public record UndeliverableEvent(
        String group,

        String topic,

        @Nullable String tag,

        String messageId,

        Map<String, String> headers,

        String body,

        int attempts,

        RuntimeException lastFailure
) {
}
