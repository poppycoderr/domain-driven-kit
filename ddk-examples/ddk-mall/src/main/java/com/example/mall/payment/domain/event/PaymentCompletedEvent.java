package com.example.mall.payment.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.payment.domain.model.PaymentId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付已完成。
 */
public record PaymentCompletedEvent(
        PaymentId paymentId,

        Long orderId,

        BigDecimal amount,

        Instant occurredOn
) implements DomainEvent {

    public PaymentCompletedEvent(PaymentId paymentId, Long orderId, BigDecimal amount) {
        this(paymentId, orderId, amount, Instant.now());
    }
}
