package com.example.mall.inventory.domain.model;

/**
 * 预占记录的状态。
 */
public enum ReservationStatus {

    /** 已预占，等待订单支付或取消 */
    RESERVED,

    /** 已释放：订单取消 */
    RELEASED,

    /** 已扣减：订单已支付 */
    CONFIRMED
}
