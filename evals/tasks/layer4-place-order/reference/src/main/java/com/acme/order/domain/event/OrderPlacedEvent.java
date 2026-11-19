package com.acme.order.domain.event;

import com.acme.order.domain.model.OrderId;
import com.ddk.core.domain.DomainEvent;

import java.time.Instant;

/**
 * 订单已下单。
 */
public record OrderPlacedEvent(
        OrderId orderId,
        long customerId,
        long amount,
        Instant occurredOn) implements DomainEvent {

    public OrderPlacedEvent(OrderId orderId, long customerId, long amount) {
        this(orderId, customerId, amount, Instant.now());
    }
}
