package com.example.mall.order.adapter.messaging;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.example.mall.order.adapter.messaging.payload.StockReservationRejectedPayload;
import com.example.mall.order.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费库存上下文的「库存预占失败」：订单随之取消。
 */
@Component
@RequiredArgsConstructor
public class StockReservationRejectedConsumer implements IntegrationEventConsumer<StockReservationRejectedPayload> {

    private final OrderService orderService;

    @Override
    public String group() {
        return "mall-order";
    }

    @Override
    public String source() {
        return "mall-inventory-events:rejected";
    }

    @Override
    public Class<StockReservationRejectedPayload> payloadType() {
        return StockReservationRejectedPayload.class;
    }

    @Override
    public void handle(ReceivedEvent<StockReservationRejectedPayload> event) {
        orderService.cancelForStock(event.payload().orderId(), event.payload().reason());
    }
}
