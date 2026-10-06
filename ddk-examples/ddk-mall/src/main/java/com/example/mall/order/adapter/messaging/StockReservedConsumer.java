package com.example.mall.order.adapter.messaging;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.example.mall.order.adapter.messaging.payload.StockReservedPayload;
import com.example.mall.order.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费库存上下文的「库存已预占」。订单的状态流转本身可以重复执行，所以不需要额外去重。
 */
@Component
@RequiredArgsConstructor
public class StockReservedConsumer implements IntegrationEventConsumer<StockReservedPayload> {

    private final OrderService orderService;

    @Override
    public String group() {
        return "mall-order";
    }

    @Override
    public String source() {
        return "mall-inventory-events:reserved";
    }

    @Override
    public Class<StockReservedPayload> payloadType() {
        return StockReservedPayload.class;
    }

    @Override
    public void handle(ReceivedEvent<StockReservedPayload> event) {
        orderService.confirmStock(event.payload().orderId());
    }
}
