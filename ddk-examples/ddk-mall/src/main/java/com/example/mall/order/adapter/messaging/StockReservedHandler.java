package com.example.mall.order.adapter.messaging;

import com.example.mall.order.adapter.messaging.payload.StockReservedPayload;
import com.example.mall.order.application.service.OrderService;
import com.example.mall.platform.messaging.MessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费库存上下文的「库存已预占」。订单的状态流转本身可以重复执行，所以这里不需要额外去重。
 */
@Component
@RequiredArgsConstructor
public class StockReservedHandler implements MessageHandler<StockReservedPayload> {

    private final OrderService orderService;

    @Override
    public String consumerGroup() {
        return "mall-order";
    }

    @Override
    public String topic() {
        return "mall-inventory-events";
    }

    @Override
    public String tag() {
        return "reserved";
    }

    @Override
    public Class<StockReservedPayload> payloadType() {
        return StockReservedPayload.class;
    }

    @Override
    public void handle(String messageId, StockReservedPayload payload) {
        orderService.confirmStock(payload.orderId());
    }
}
