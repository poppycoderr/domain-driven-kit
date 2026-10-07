package com.example.mall.payment.adapter.messaging;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.example.mall.payment.adapter.messaging.payload.CancelledOrderPayload;
import com.example.mall.payment.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消费订单上下文的「订单已取消」：关闭支付单，已经收到的钱退回去。
 */
@Component
@RequiredArgsConstructor
public class CancelledOrderConsumer implements IntegrationEventConsumer<CancelledOrderPayload> {

    private final PaymentService paymentService;

    @Override
    public String group() {
        return "mall-payment";
    }

    @Override
    public String source() {
        return "mall-order-events:cancelled";
    }

    @Override
    public Class<CancelledOrderPayload> payloadType() {
        return CancelledOrderPayload.class;
    }

    @Override
    public void handle(ReceivedEvent<CancelledOrderPayload> event) {
        paymentService.cancelForOrder(event.payload().orderId());
    }
}
