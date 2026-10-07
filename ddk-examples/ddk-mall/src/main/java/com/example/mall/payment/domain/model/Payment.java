package com.example.mall.payment.domain.model;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.example.mall.payment.domain.error.PaymentError;
import com.example.mall.payment.domain.event.PaymentCompletedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 支付单：一个订单的一笔应付款。订单在这里只是一个编号，金额和支付期限来自订单上下文发出的消息。
 */
public class Payment extends AggregateRoot<PaymentId> {

    private Long orderId;

    private Long customerId;

    private BigDecimal amount;

    private PaymentStatus status;

    private Instant expiresAt;

    private String channelTradeNo;

    private Instant paidAt;

    private Payment() {
    }

    public static Payment request(PaymentId id, Long orderId, Long customerId, BigDecimal amount, Instant expiresAt) {
        Payment payment = new Payment();
        payment.assignId(Objects.requireNonNull(id, "id"));
        payment.orderId = Objects.requireNonNull(orderId, "orderId");
        payment.customerId = Objects.requireNonNull(customerId, "customerId");
        payment.amount = Objects.requireNonNull(amount, "amount");
        payment.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public static Payment restore(PaymentId id, Long orderId, Long customerId, BigDecimal amount, PaymentStatus status, Instant expiresAt,
                                  String channelTradeNo, Instant paidAt, Long version) {
        Payment payment = new Payment();
        payment.assignId(Objects.requireNonNull(id, "id"));
        payment.orderId = orderId;
        payment.customerId = customerId;
        payment.amount = amount;
        payment.status = status;
        payment.expiresAt = expiresAt;
        payment.channelTradeNo = channelTradeNo;
        payment.paidAt = paidAt;
        payment.assignVersion(version);
        return payment;
    }

    /**
     * 现在还能不能支付。在请求渠道扣款之前调用，避免对已经关闭或过期的支付单扣款。
     */
    public void ensurePayable(Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new BusinessException(PaymentError.PAYMENT_NOT_PAYABLE, status);
        }
        if (!now.isBefore(expiresAt)) {
            throw new BusinessException(PaymentError.PAYMENT_EXPIRED, orderId);
        }
    }

    /**
     * 渠道扣款成功，登记 {@link PaymentCompletedEvent}。
     */
    public void complete(String channelTradeNo, Instant now) {
        ensurePayable(now);
        this.status = PaymentStatus.PAID;
        this.channelTradeNo = Objects.requireNonNull(channelTradeNo, "channelTradeNo");
        this.paidAt = now;
        registerEvent(new PaymentCompletedEvent(id(), orderId, amount));
    }

    /**
     * 订单取消了，还没支付的支付单随之关闭。返回是否发生了变化，重复的取消通知返回 false。
     */
    public boolean close() {
        if (status != PaymentStatus.PENDING) {
            return false;
        }
        this.status = PaymentStatus.CLOSED;
        return true;
    }

    /**
     * 钱已经由渠道退回。
     */
    public void markRefunded() {
        if (status != PaymentStatus.PAID) {
            throw new BusinessException(PaymentError.PAYMENT_NOT_REFUNDABLE, status);
        }
        this.status = PaymentStatus.REFUNDED;
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public boolean belongsTo(Long customerId) {
        return this.customerId.equals(customerId);
    }

    public Long orderId() {
        return orderId;
    }

    public Long customerId() {
        return customerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String channelTradeNo() {
        return channelTradeNo;
    }

    public Instant paidAt() {
        return paidAt;
    }
}
