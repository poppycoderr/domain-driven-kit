package com.example.mall.order.infrastructure.search;

import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.OrderStatus;
import com.example.mall.order.domain.model.OrderSummary;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单在 Elasticsearch 里的文档。字段都是简单类型，金额存成字符串以免丢失精度，时间存成毫秒数。
 */
public record OrderDocument(
        Long orderId,

        Long customerId,

        String status,

        String totalAmount,

        Integer itemCount,

        String productNames,

        Long expiresAt
) {

    static OrderDocument from(OrderSummary summary) {
        return new OrderDocument(summary.orderId(), summary.customerId(), summary.status().name(),
                summary.totalAmount().amount().toPlainString(), summary.itemCount(), summary.productNames(),
                summary.expiresAt() == null ? null : summary.expiresAt().toEpochMilli());
    }

    OrderSummary toSummary() {
        return new OrderSummary(orderId, customerId, OrderStatus.valueOf(status), new Money(new BigDecimal(totalAmount)), itemCount,
                productNames, expiresAt == null ? null : Instant.ofEpochMilli(expiresAt));
    }
}
