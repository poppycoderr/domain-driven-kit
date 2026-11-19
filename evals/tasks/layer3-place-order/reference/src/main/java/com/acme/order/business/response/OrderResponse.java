package com.acme.order.business.response;

import com.acme.order.business.model.Order;

/**
 * 订单。
 */
public record OrderResponse(
        String id,
        long customerId,
        long amount,
        String status) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(String.valueOf(order.id().value()), order.customerId(), order.amount(), order.status().name());
    }
}
