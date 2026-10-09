package com.example.mall.order.application.response;

import com.example.mall.order.domain.model.OrderSummary;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 搜索结果里的一个订单。
 */
public record OrderSummaryResponse(
        Long orderId,

        String status,

        BigDecimal totalAmount,

        int itemCount,

        String productNames,

        Instant expiresAt
) {

    public static OrderSummaryResponse from(OrderSummary summary) {
        return new OrderSummaryResponse(summary.orderId(), summary.status().name(), summary.totalAmount().amount(), summary.itemCount(),
                summary.productNames(), summary.expiresAt());
    }
}
