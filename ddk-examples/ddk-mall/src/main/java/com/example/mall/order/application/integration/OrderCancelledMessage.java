package com.example.mall.order.application.integration;

import com.ddk.core.domain.IntegrationEvent;
import com.example.mall.platform.messaging.IntegrationMessage;

/**
 * 对外契约：订单已取消。
 */
@IntegrationEvent(value = "mall-order-events:cancelled", key = "orderId", id = "eventId", type = "order.cancelled")
public record OrderCancelledMessage(
        String eventId,

        Long orderId,

        String reason
) implements IntegrationMessage {
}
