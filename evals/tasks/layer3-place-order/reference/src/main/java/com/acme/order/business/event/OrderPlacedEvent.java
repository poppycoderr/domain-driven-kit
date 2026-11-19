package com.acme.order.business.event;

import com.acme.order.business.model.OrderId;
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
