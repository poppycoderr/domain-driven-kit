package com.example.mall.order.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.order.domain.model.OrderId;

import java.time.Instant;

/**
 * 订单已取消。
 */
public record OrderCancelledEvent(
        OrderId orderId,

        String reason,

        Instant occurredOn
) implements DomainEvent {

    public OrderCancelledEvent(OrderId orderId, String reason) {
        this(orderId, reason, Instant.now());
    }
}
