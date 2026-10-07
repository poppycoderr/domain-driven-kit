package com.example.mall.payment.application.handler;

import com.example.mall.payment.application.integration.PaymentCompletedMessage;
import com.example.mall.payment.domain.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 把支付的领域事件翻译成对外的消息，在支付的事务里登记，和支付单一起提交。
 */
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final ApplicationEventPublisher publisher;

    @EventListener
    public void on(PaymentCompletedEvent event) {
        publisher.publishEvent(new PaymentCompletedMessage(UUID.randomUUID().toString(), event.orderId(), event.amount()));
    }
}
