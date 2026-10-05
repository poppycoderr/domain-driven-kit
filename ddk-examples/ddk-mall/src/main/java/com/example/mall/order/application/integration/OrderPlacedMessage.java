package com.example.mall.order.application.integration;

import com.ddk.core.domain.IntegrationEvent;
import com.example.mall.platform.messaging.IntegrationMessage;

import java.util.List;

/**
 * 对外契约：订单已下单。字段都是基本类型，不暴露订单上下文的领域类型。
 */
@IntegrationEvent(value = "mall-order-events:placed", key = "orderId", id = "eventId", type = "order.placed")
public record OrderPlacedMessage(
        String eventId,

        Long orderId,

        Long customerId,

        List<Line> lines
) implements IntegrationMessage {

    public record Line(
            String skuId,

            int quantity
    ) {
    }
}
