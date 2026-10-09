package com.example.mall.order.domain.model;

import java.time.Instant;

/**
 * 订单在列表和搜索结果里的样子：一行就能展示的摘要。它是读模型里的数据，由订单聚合生成，不能反过来用它修改订单。
 *
 * @param orderId      订单号
 * @param customerId   顾客
 * @param status       状态
 * @param totalAmount  应付金额
 * @param itemCount    商品件数，各行数量之和
 * @param productNames 各行的商品名称，用顿号连接，搜索按它匹配
 * @param expiresAt    支付期限
 */
public record OrderSummary(
        Long orderId,

        Long customerId,

        OrderStatus status,

        Money totalAmount,

        int itemCount,

        String productNames,

        Instant expiresAt
) {

    public static OrderSummary of(Order order) {
        return new OrderSummary(order.id().value(), order.customerId(), order.status(), order.totalAmount(),
                order.lines().stream().mapToInt(OrderLine::quantity).sum(),
                String.join("、", order.lines().stream().map(OrderLine::productName).toList()), order.expiresAt());
    }
}
