package com.example.mall.order.application.integration;

import com.ddk.core.domain.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 对外契约：订单等待支付。带上应付金额和支付期限，支付上下文据此建立支付单，不需要回头查订单。
 */
@IntegrationEvent(value = "mall-order-events:awaiting-payment", key = "orderId", id = "eventId", type = "order.awaiting-payment")
public record OrderAwaitingPaymentMessage(
        String eventId,

        Long orderId,

        Long customerId,

        BigDecimal amount,

        Instant expiresAt
) {
}
