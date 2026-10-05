package com.example.mall.inventory.application.integration;

import com.ddk.core.domain.IntegrationEvent;
import com.example.mall.platform.messaging.IntegrationMessage;

/**
 * 对外契约：订单的库存已全部预占。
 */
@IntegrationEvent(value = "mall-inventory-events:reserved", key = "orderId", id = "eventId", type = "inventory.stock-reserved")
public record StockReservedMessage(
        String eventId,

        Long orderId
) implements IntegrationMessage {
}
