package com.example.mall.order.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderLine;

import java.time.Instant;
import java.util.List;

/**
 * 订单已下单。
 */
public record OrderPlacedEvent(
        OrderId orderId,

        Long customerId,

        Money totalAmount,

        List<OrderLine> lines,

        Instant occurredOn
) implements DomainEvent {

    public OrderPlacedEvent(OrderId orderId, Long customerId, Money totalAmount, List<OrderLine> lines) {
        this(orderId, customerId, totalAmount, lines, Instant.now());
    }
}
