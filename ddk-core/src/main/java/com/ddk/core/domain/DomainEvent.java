package com.ddk.core.domain;

import java.time.Instant;

/**
 * 领域事件。
 * <p>
 * 事件表达的是「已经发生的事实」，命名用过去式：{@code OrderPaidEvent} 而不是 {@code PayOrderEvent}。
 * DDK 只定义契约，不规定传输方式；发布时机见 {@link DomainEventPublisher}。
 *
 * @author Elijah Du
 * @date 2026/9/11
 */
public interface DomainEvent {

    /**
     * 事件发生时刻。
     * <p>
     * 由实现者在构造时固定，表示「事实发生」的时间，不是「事件被发布」的时间——
     * 两者之间隔着一次事务提交。
     */
    Instant occurredOn();

    /**
     * 事件类型标识，用于日志、审计与消息路由。
     */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
