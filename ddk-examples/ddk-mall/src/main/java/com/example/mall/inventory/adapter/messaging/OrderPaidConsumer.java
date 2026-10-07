package com.example.mall.inventory.adapter.messaging;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.example.mall.inventory.adapter.messaging.payload.OrderPaidPayload;
import com.example.mall.inventory.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费订单上下文的「订单已支付」：把预占的库存扣掉。扣减本身可以重复执行，不需要额外去重。
 */
@Component
@RequiredArgsConstructor
public class OrderPaidConsumer implements IntegrationEventConsumer<OrderPaidPayload> {

    private final InventoryService inventoryService;

    @Override
    public String group() {
        return "mall-inventory";
    }

    @Override
    public String source() {
        return "mall-order-events:paid";
    }

    @Override
    public Class<OrderPaidPayload> payloadType() {
        return OrderPaidPayload.class;
    }

    @Override
    public void handle(ReceivedEvent<OrderPaidPayload> event) {
        inventoryService.confirm(event.payload().orderId());
    }
}
