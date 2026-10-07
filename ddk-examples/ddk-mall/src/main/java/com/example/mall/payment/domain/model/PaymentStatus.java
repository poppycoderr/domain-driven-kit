package com.example.mall.payment.domain.model;

/**
 * 支付单状态。
 */
public enum PaymentStatus {

    /** 等待顾客支付 */
    PENDING,

    /** 已支付 */
    PAID,

    /** 没有支付就关闭了：订单被取消或超时 */
    CLOSED,

    /** 支付之后订单被取消，钱已退回 */
    REFUNDED
}
