package com.example.mall.payment.domain.error;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付上下文的错误码。
 */
@Getter
@AllArgsConstructor
public enum PaymentError implements ErrorCode {

    PAYMENT_NOT_FOUND("订单 {0} 还没有可以支付的支付单"),
    PAYMENT_NOT_PAYABLE("当前状态的支付单不能支付：{0}"),
    PAYMENT_EXPIRED("订单 {0} 已经过了支付期限"),
    PAYMENT_DECLINED("支付渠道拒绝了这笔支付：{0}"),
    PAYMENT_NOT_REFUNDABLE("当前状态的支付单不能退款：{0}"),
    ;

    private final String message;
}
