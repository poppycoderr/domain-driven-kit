package com.example.mall.inventory.application.integration;

import com.ddk.core.domain.IntegrationEvent;
import com.example.mall.platform.messaging.IntegrationMessage;

/**
 * 对外契约：订单的库存预占失败，一件都没有占。
 */
@IntegrationEvent(value = "mall-inventory-events:rejected", key = "orderId", id = "eventId", type = "inventory.stock-reservation-rejected")
public record StockReservationRejectedMessage(
        String eventId,

        Long orderId,

        String reason
) implements IntegrationMessage {
}
