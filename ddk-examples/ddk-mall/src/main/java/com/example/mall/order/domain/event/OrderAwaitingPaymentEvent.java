package com.example.mall.order.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.OrderId;

import java.time.Instant;

/**
 * 订单的库存已预占，等待支付。
 */
public record OrderAwaitingPaymentEvent(
        OrderId orderId,

        Long customerId,

        Money amount,

        Instant expiresAt,

        Instant occurredOn
) implements DomainEvent {

    public OrderAwaitingPaymentEvent(OrderId orderId, Long customerId, Money amount, Instant expiresAt) {
        this(orderId, customerId, amount, expiresAt, Instant.now());
    }
}
