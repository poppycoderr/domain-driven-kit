package com.example.mall.order.adapter.messaging.payload;

/**
 * 订单上下文眼里的「支付已完成」消息，只声明用得到的字段。
 */
public record PaymentCompletedPayload(
        Long orderId
) {
}
