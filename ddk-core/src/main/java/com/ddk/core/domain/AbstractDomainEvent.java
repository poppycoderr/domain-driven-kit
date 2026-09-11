package com.ddk.core.domain;

import java.time.Instant;

/**
 * {@link DomainEvent} 的抽象实现，提供 {@code occurredOn} 的默认行为。
 * <p>
 * 事件用 {@code record} 实现会更简洁，但 {@code record} 不能继承类，
 * 所以两种写法都保留：
 *
 * <pre>{@code
 * // 写法一：record + 显式 occurredOn 字段
 * public record OrderPaidEvent(OrderId orderId, Money amount, Instant occurredOn)
 *         implements DomainEvent {
 *     public OrderPaidEvent(OrderId orderId, Money amount) {
 *         this(orderId, amount, Instant.now());
 *     }
 * }
 *
 * // 写法二：继承 AbstractDomainEvent
 * public class OrderCancelledEvent extends AbstractDomainEvent {
 *     private final OrderId orderId;
 *     private final String reason;
 * }
 * }</pre>
 *
 * @author Elijah Du
 * @date 2026/9/11
 */
public abstract class AbstractDomainEvent implements DomainEvent {

    private final Instant occurredOn;

    protected AbstractDomainEvent() {
        this(Instant.now());
    }

    /**
     * 供事件重放或补偿场景指定历史时刻。
     */
    protected AbstractDomainEvent(Instant occurredOn) {
        if (occurredOn == null) {
            throw new IllegalArgumentException("事件发生时刻不能为 null");
        }
        this.occurredOn = occurredOn;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}
