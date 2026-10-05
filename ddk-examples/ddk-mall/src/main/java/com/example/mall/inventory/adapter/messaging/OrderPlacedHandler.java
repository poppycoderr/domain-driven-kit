package com.example.mall.inventory.adapter.messaging;

import com.example.mall.inventory.adapter.messaging.payload.OrderPlacedPayload;
import com.example.mall.inventory.application.command.ReserveStockCommand;
import com.example.mall.inventory.application.service.InventoryService;
import com.example.mall.platform.messaging.MessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费订单上下文的「订单已下单」：为订单预占库存。
 */
@Component
@RequiredArgsConstructor
public class OrderPlacedHandler implements MessageHandler<OrderPlacedPayload> {

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
        return "placed";
    }

    @Override
    public Class<OrderPlacedPayload> payloadType() {
        return OrderPlacedPayload.class;
    }

    @Override
    public void handle(String messageId, OrderPlacedPayload payload) {
        inventoryService.reserveForOrder(messageId, new ReserveStockCommand(payload.orderId(),
                payload.lines().stream().map(line -> new ReserveStockCommand.Line(line.skuId(), line.quantity())).toList()));
    }
}
