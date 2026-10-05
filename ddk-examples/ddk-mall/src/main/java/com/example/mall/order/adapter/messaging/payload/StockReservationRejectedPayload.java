package com.example.mall.order.adapter.messaging.payload;

/**
 * 订单上下文眼里的「库存预占失败」消息。
 */
public record StockReservationRejectedPayload(
        Long orderId,

        String reason
) {
}
