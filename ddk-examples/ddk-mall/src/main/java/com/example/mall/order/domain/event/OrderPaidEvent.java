package com.example.mall.order.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.order.domain.model.OrderId;

import java.time.Instant;

/**
 * 订单已支付。
 */
public record OrderPaidEvent(
        OrderId orderId,

        Instant occurredOn
) implements DomainEvent {

    public OrderPaidEvent(OrderId orderId) {
        this(orderId, Instant.now());
    }
}
