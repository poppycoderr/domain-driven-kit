package com.example.mall.order.adapter.messaging.payload;

/**
 * 订单上下文眼里的「库存已预占」消息，只声明用得到的字段。
 */
public record StockReservedPayload(
        Long orderId
) {
}
