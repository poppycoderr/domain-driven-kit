package com.example.mall.inventory.adapter.messaging.payload;

/**
 * 库存上下文眼里的「订单已取消」消息。
 */
public record OrderCancelledPayload(
        Long orderId
) {
}
