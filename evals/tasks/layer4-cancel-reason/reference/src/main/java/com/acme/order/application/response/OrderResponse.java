package com.acme.order.application.response;

import com.acme.order.domain.model.Order;

/**
 * 订单。
 */
public record OrderResponse(
        String id,
        long customerId,
        long amount,
        String status,
        String cancelReason) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(String.valueOf(order.id().value()), order.customerId(), order.amount(), order.status().name(), order.cancelReason());
    }
}
