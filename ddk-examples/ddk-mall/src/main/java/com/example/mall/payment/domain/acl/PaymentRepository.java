package com.example.mall.payment.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.mall.payment.domain.model.Payment;
import com.example.mall.payment.domain.model.PaymentId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 支付单仓储。一个订单只有一张支付单。
 */
public interface PaymentRepository extends GenericRepository<Payment, PaymentId> {

    Optional<Payment> findByOrder(Long orderId);

    /**
     * 可能在渠道扣过款、却还没有记账或退回的支付单：请求扣款早于 {@code requestedBefore}，状态仍是待支付或已关闭。
     */
    List<Payment> findAwaitingReconciliation(Instant requestedBefore, int limit);
}
