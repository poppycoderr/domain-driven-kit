package com.example.mall.payment.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.mall.payment.domain.model.Payment;
import com.example.mall.payment.domain.model.PaymentId;

import java.util.Optional;

/**
 * 支付单仓储。一个订单只有一张支付单。
 */
public interface PaymentRepository extends GenericRepository<Payment, PaymentId> {

    Optional<Payment> findByOrder(Long orderId);
}
