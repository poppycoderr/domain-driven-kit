package com.example.mall.payment.adapter.messaging.payload;

/**
 * 支付上下文眼里的「订单已取消」消息。
 */
public record CancelledOrderPayload(
        Long orderId
) {
}
