package com.example.mall.inventory.adapter.messaging;

import com.example.mall.inventory.adapter.messaging.payload.OrderCancelledPayload;
import com.example.mall.inventory.application.service.InventoryService;
import com.example.mall.platform.messaging.MessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费订单上下文的「订单已取消」：释放订单预占的库存。释放本身可以重复执行，不需要额外去重。
 */
@Component
@RequiredArgsConstructor
public class OrderCancelledHandler implements MessageHandler<OrderCancelledPayload> {

    private final InventoryService inventoryService;

    @Override
    public String consumerGroup() {
        return "mall-inventory";
    }

    @Override
    public String topic() {
        return "mall-order-events";
    }

    @Override
    public String tag() {
        return "cancelled";
    }

    @Override
    public Class<OrderCancelledPayload> payloadType() {
        return OrderCancelledPayload.class;
    }

    @Override
    public void handle(String messageId, OrderCancelledPayload payload) {
        inventoryService.release(payload.orderId());
    }
}
