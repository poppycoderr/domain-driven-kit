package com.acme.order.business.error;

import com.ddk.core.exception.ErrorCode;

/**
 * 订单域错误码。
 */
public enum OrderError implements ErrorCode {
    ORDER_NOT_FOUND("订单不存在：{0}"),
    INVALID_AMOUNT("订单金额必须大于 0：{0}"),
    ORDER_NOT_CANCELLABLE("订单当前状态不能取消：{0}");

    private final String message;

    OrderError(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
