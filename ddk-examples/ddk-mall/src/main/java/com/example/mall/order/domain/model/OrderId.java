package com.example.mall.order.domain.model;

import com.ddk.core.domain.Identifier;

/**
 * 订单标识。
 */
public final class OrderId extends Identifier<Long> {

    private OrderId(Long value) {
        super(value);
        if (value <= 0) {
            throw new IllegalArgumentException("OrderId must be positive, got " + value);
        }
    }

    public static OrderId of(Long value) {
        return new OrderId(value);
    }
}
