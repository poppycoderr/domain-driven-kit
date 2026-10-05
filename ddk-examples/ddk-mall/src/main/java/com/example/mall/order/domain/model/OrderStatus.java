package com.example.mall.order.domain.model;

/**
 * 订单状态。
 */
public enum OrderStatus {

    /** 已下单，等待库存确认 */
    PENDING_STOCK,

    /** 库存已预占，等待支付 */
    PENDING_PAYMENT,

    /** 已取消：顾客取消，或库存不足 */
    CANCELLED
}
