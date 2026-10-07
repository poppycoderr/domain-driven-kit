package com.example.mall.order.domain.model;

/**
 * 订单状态。
 */
public enum OrderStatus {

    /** 已下单，等待库存确认 */
    PENDING_STOCK,

    /** 库存已预占，等待支付 */
    PENDING_PAYMENT,

    /** 已支付，库存随后扣减 */
    PAID,

    /** 已取消：顾客取消、库存不足，或超过支付期限 */
    CANCELLED
}
