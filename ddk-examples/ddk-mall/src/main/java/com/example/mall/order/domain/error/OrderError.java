package com.example.mall.order.domain.error;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单上下文的错误码。
 */
@Getter
@AllArgsConstructor
public enum OrderError implements ErrorCode {

    ORDER_NOT_FOUND("订单不存在：{0}"),
    ORDER_EMPTY("订单至少要有一件商品"),
    DUPLICATE_SKU("同一件商品在订单里出现了多次：{0}"),
    INVALID_QUANTITY("商品 {0} 的数量必须大于 0：{1}"),
    INVALID_AMOUNT("金额不合法：{0}"),
    PRODUCT_NOT_FOUND("商品不存在：{0}"),
    ORDER_NOT_CANCELLABLE("当前状态的订单不能取消：{0}"),
    ;

    private final String message;
}
