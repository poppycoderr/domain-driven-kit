package com.example.mall.payment.application.integration;

import com.ddk.core.domain.IntegrationEvent;

import java.math.BigDecimal;

/**
 * 对外契约：订单的支付已完成。
 */
@IntegrationEvent(value = "mall-payment-events:completed", key = "orderId", id = "eventId", type = "payment.completed")
public record PaymentCompletedMessage(
        String eventId,

        Long orderId,

        BigDecimal amount
) {
}
