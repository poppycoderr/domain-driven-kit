package com.example.mall.payment.application.response;

import com.example.mall.payment.domain.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付单对外响应。
 */
public record PaymentResponse(
        Long id,

        Long orderId,

        BigDecimal amount,

        String status,

        Instant expiresAt,

        String channelTradeNo,

        Instant paidAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.id().value(), payment.orderId(), payment.amount(), payment.status().name(), payment.expiresAt(),
                payment.channelTradeNo(), payment.paidAt());
    }
}
