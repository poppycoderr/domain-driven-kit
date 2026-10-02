package com.example.mall.order.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.OrderId;

import java.time.Instant;

/**
 * 订单已下单。
 */
public record OrderPlacedEvent(
        OrderId orderId,

        Long customerId,

        Money totalAmount,

        Instant occurredOn
) implements DomainEvent {

    public OrderPlacedEvent(OrderId orderId, Long customerId, Money totalAmount) {
        this(orderId, customerId, totalAmount, Instant.now());
    }
}
