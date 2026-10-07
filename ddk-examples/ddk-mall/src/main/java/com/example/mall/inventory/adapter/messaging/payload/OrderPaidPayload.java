package com.example.mall.inventory.adapter.messaging.payload;

/**
 * 库存上下文眼里的「订单已支付」消息。
 */
public record OrderPaidPayload(
        Long orderId
) {
}
