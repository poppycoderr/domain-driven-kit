package com.example.mall.payment.adapter.messaging.payload;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付上下文眼里的「订单等待支付」消息。
 */
public record OrderAwaitingPaymentPayload(
        Long orderId,

        Long customerId,

        BigDecimal amount,

        Instant expiresAt
) {
}
