package com.example.mall.order.adapter.messaging;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.example.mall.order.adapter.messaging.payload.PaymentCompletedPayload;
import com.example.mall.order.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费支付上下文的「支付已完成」。订单的状态流转本身可以重复执行，所以不需要额外去重。
 */
@Component
@RequiredArgsConstructor
public class PaymentCompletedConsumer implements IntegrationEventConsumer<PaymentCompletedPayload> {

    private final OrderService orderService;

    @Override
    public String group() {
        return "mall-order";
    }

    @Override
    public String source() {
        return "mall-payment-events:completed";
    }

    @Override
    public Class<PaymentCompletedPayload> payloadType() {
        return PaymentCompletedPayload.class;
    }

    @Override
    public void handle(ReceivedEvent<PaymentCompletedPayload> event) {
        orderService.markPaid(event.payload().orderId());
    }
}
