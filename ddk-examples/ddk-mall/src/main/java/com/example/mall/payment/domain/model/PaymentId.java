package com.example.mall.payment.domain.model;

import com.ddk.core.domain.Identifier;

/**
 * 支付单的标识。它同时是发给支付渠道的商户单号：同一个支付单重复请求扣款，渠道只扣一次。
 */
public final class PaymentId extends Identifier<Long> {

    private PaymentId(Long value) {
        super(value);
    }

    public static PaymentId of(Long value) {
        return new PaymentId(value);
    }
}
