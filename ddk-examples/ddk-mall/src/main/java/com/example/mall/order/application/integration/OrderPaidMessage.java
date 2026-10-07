package com.example.mall.order.application.integration;

import com.ddk.core.domain.IntegrationEvent;

/**
 * 对外契约：订单已支付。
 */
@IntegrationEvent(value = "mall-order-events:paid", key = "orderId", id = "eventId", type = "order.paid")
public record OrderPaidMessage(
        String eventId,

        Long orderId
) {
}
