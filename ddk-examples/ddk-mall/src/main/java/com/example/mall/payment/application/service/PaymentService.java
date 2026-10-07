package com.example.mall.payment.application.service;

import com.ddk.core.exception.BusinessException;
import com.example.mall.payment.application.response.PaymentResponse;
import com.example.mall.payment.domain.acl.PaymentChannel;
import com.example.mall.payment.domain.acl.PaymentIdGenerator;
import com.example.mall.payment.domain.acl.PaymentRepository;
import com.example.mall.payment.domain.error.PaymentError;
import com.example.mall.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付应用服务。
 * <p>
 * 请求渠道和保存支付单不在一个原子操作里：渠道扣款成功之后，保存可能失败（例如并发的另一个请求先保存了，乐观锁冲突）。
 * 这里不靠分布式事务，靠两点：渠道按支付单标识去重，重复请求不会多扣；支付单的状态和乐观锁保证只有一个请求把它记成已支付。
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private final PaymentIdGenerator paymentIdGenerator;

    private final PaymentChannel paymentChannel;

    public PaymentResponse get(Long customerId, Long orderId) {
        return PaymentResponse.from(requireOwnPayment(customerId, orderId));
    }

    @Transactional
    public PaymentResponse pay(Long customerId, Long orderId) {
        Payment payment = requireOwnPayment(customerId, orderId);
        Instant now = Instant.now();
        payment.ensurePayable(now);
        PaymentChannel.Charge charge = paymentChannel.charge(payment);
        if (!charge.approved()) {
            throw new BusinessException(PaymentError.PAYMENT_DECLINED, charge.reason());
        }
        payment.complete(charge.tradeNo(), now);
        return PaymentResponse.from(paymentRepository.update(payment));
    }

    /**
     * 订单等待支付：为它建立支付单。一个订单只有一张支付单，重复的消息什么都不做；
     * 两条重复消息同时到达时，后一个会撞上订单号的唯一索引而失败，重投之后走到「已经存在」。
     */
    @Transactional
    public void open(Long orderId, Long customerId, BigDecimal amount, Instant expiresAt) {
        if (paymentRepository.findByOrder(orderId).isEmpty()) {
            paymentRepository.create(Payment.request(paymentIdGenerator.nextId(), orderId, customerId, amount, expiresAt));
        }
    }

    /**
     * 订单已取消：没支付的支付单关闭，已经支付的把钱退回去。两种情况都可以重复执行。
     * <p>
     * 订单没有支付单时什么都不做：订单可能在库存确认之前就取消了。
     */
    @Transactional
    public void cancelForOrder(Long orderId) {
        paymentRepository.findByOrder(orderId).ifPresent(payment -> {
            if (payment.isPaid()) {
                paymentChannel.refund(payment);
                payment.markRefunded();
                paymentRepository.update(payment);
            } else if (payment.close()) {
                paymentRepository.update(payment);
            }
        });
    }

    /**
     * 别人的支付单与不存在的一样处理。
     */
    private Payment requireOwnPayment(Long customerId, Long orderId) {
        return paymentRepository.findByOrder(orderId)
                .filter(payment -> payment.belongsTo(customerId))
                .orElseThrow(() -> new BusinessException(PaymentError.PAYMENT_NOT_FOUND, orderId));
    }
}
