package com.example.mall.payment.adapter.messaging;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.example.mall.payment.adapter.messaging.payload.OrderAwaitingPaymentPayload;
import com.example.mall.payment.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费订单上下文的「订单等待支付」：建立支付单。一个订单只有一张支付单，重复的消息不会建出第二张。
 */
@Component
@RequiredArgsConstructor
public class OrderAwaitingPaymentConsumer implements IntegrationEventConsumer<OrderAwaitingPaymentPayload> {

    private final PaymentService paymentService;

    @Override
    public String group() {
        return "mall-payment";
    }

    @Override
    public String source() {
        return "mall-order-events:awaiting-payment";
    }

    @Override
    public Class<OrderAwaitingPaymentPayload> payloadType() {
        return OrderAwaitingPaymentPayload.class;
    }

    @Override
    public void handle(ReceivedEvent<OrderAwaitingPaymentPayload> event) {
        OrderAwaitingPaymentPayload payload = event.payload();
        paymentService.open(payload.orderId(), payload.customerId(), payload.amount(), payload.expiresAt());
    }
}
