package com.example.mall.payment.domain.acl;

import com.example.mall.payment.domain.model.PaymentId;

/**
 * 支付单标识生成器。
 */
public interface PaymentIdGenerator {

    PaymentId nextId();
}
