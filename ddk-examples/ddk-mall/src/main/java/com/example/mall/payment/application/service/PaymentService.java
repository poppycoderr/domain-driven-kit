package com.example.mall.payment.application.service;

import com.ddk.core.exception.BusinessException;
import com.example.mall.payment.application.response.PaymentResponse;
import com.example.mall.payment.domain.acl.PaymentChannel;
import com.example.mall.payment.domain.acl.PaymentIdGenerator;
import com.example.mall.payment.domain.acl.PaymentRepository;
import com.example.mall.payment.domain.error.PaymentError;
import com.example.mall.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付应用服务。
 * <p>
 * 渠道扣款是外部副作用，不随本地事务回滚，所以支付分三步，而不是包在一个事务里：
 * <ol>
 *     <li>记下「要请求扣款了」并单独提交；</li>
 *     <li>请求渠道扣款，不在事务里；</li>
 *     <li>在新事务里结算：支付单还在等待支付就记成已支付；期间订单被取消、支付单已经关闭，就把钱退回去。</li>
 * </ol>
 * 第三步没有完成（进程崩溃、保存冲突）时，第一步留下的记录让 {@link #reconcile} 找到这张支付单，向渠道查询结果后补做第三步。
 * 渠道按支付单标识去重，所以重复扣款、重复退款、重复对账都是安全的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private final PaymentIdGenerator paymentIdGenerator;

    private final PaymentChannel paymentChannel;

    private final TransactionTemplate transaction;

    public PaymentResponse get(Long customerId, Long orderId) {
        return PaymentResponse.from(requireOwnPayment(customerId, orderId));
    }

    /**
     * 支付。结算时发现订单在扣款期间被取消了，钱会被退回，返回的支付单状态是 {@code REFUNDED}。
     */
    public PaymentResponse pay(Long customerId, Long orderId) {
        Payment requested = transaction.execute(status -> {
            Payment payment = requireOwnPayment(customerId, orderId);
            payment.requestCharge(Instant.now());
            return paymentRepository.update(payment);
        });
        PaymentChannel.Charge charge = paymentChannel.charge(requested);
        if (!charge.approved()) {
            throw new BusinessException(PaymentError.PAYMENT_DECLINED, charge.reason());
        }
        return PaymentResponse.from(transaction.execute(status -> settle(orderId, charge.tradeNo())));
    }

    /**
     * 和渠道对账：找出请求扣款早于 {@code requestedBefore}、却还没有记账或退回的支付单，逐张向渠道查询，补做结算；
     * 渠道没有扣款的，不再对账。每张支付单在自己的事务里处理，一张失败不影响其他的，下次再试。
     *
     * @return 处理了的支付单数量
     */
    public int reconcile(Instant requestedBefore, int limit) {
        int reconciled = 0;
        for (Payment candidate : paymentRepository.findAwaitingReconciliation(requestedBefore, limit)) {
            try {
                PaymentChannel.Lookup lookup = paymentChannel.query(candidate);
                transaction.executeWithoutResult(status -> {
                    if (lookup.charged()) {
                        settle(candidate.orderId(), lookup.tradeNo());
                    } else {
                        Payment payment = paymentRepository.findByOrder(candidate.orderId()).orElseThrow();
                        if (payment.awaitsReconciliation()) {
                            payment.chargeNotFound();
                            paymentRepository.update(payment);
                        }
                    }
                });
                reconciled++;
            } catch (RuntimeException e) {
                log.warn("Could not reconcile payment {} of order {}, will try again", candidate.id().value(), candidate.orderId(), e);
            }
        }
        return reconciled;
    }

    /**
     * 渠道已经扣款，把结果落到支付单上。每次都重新读取：从请求扣款到这里，订单可能已经被取消、支付单已经关闭。
     */
    private Payment settle(Long orderId, String tradeNo) {
        Payment payment = paymentRepository.findByOrder(orderId).orElseThrow();
        switch (payment.status()) {
            case PENDING -> {
                payment.complete(tradeNo, Instant.now());
                return paymentRepository.update(payment);
            }
            case CLOSED -> {
                paymentChannel.refund(payment);
                payment.markRefunded();
                log.info("Order {} was cancelled while payment {} was being charged; refunded", orderId, payment.id().value());
                return paymentRepository.update(payment);
            }
            default -> {
                return payment;
            }
        }
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
