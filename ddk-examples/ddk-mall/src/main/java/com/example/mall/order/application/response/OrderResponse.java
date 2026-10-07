package com.example.mall.order.application.response;

import com.example.mall.order.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单对外响应。
 */
public record OrderResponse(
        Long id,

        Long customerId,

        String status,

        BigDecimal totalAmount,

        String cancelReason,

        Instant expiresAt,

        List<Line> lines
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id().value(), order.customerId(), order.status().name(), order.totalAmount().amount(),
                order.cancelReason(), order.expiresAt(), order.lines().stream()
                .map(line -> new Line(line.skuId(), line.productName(), line.unitPrice().amount(), line.quantity(), line.subtotal().amount()))
                .toList());
    }

    public record Line(
            String skuId,

            String productName,

            BigDecimal unitPrice,

            int quantity,

            BigDecimal subtotal
    ) {
    }
}
