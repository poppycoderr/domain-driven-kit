package com.example.mall.inventory.domain.error;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存上下文的错误码。
 */
@Getter
@AllArgsConstructor
public enum InventoryError implements ErrorCode {

    STOCK_NOT_FOUND("没有这个 SKU 的库存：{0}"),
    INSUFFICIENT_STOCK("库存不足：{0} 可售 {1}，需要 {2}"),
    INVALID_QUANTITY("数量必须大于 0：{0}"),
    RESERVATION_NOT_FOUND("订单 {0} 没有预占记录"),
    RESERVATION_ALREADY_CONFIRMED("订单 {0} 的库存已经扣减，不能释放"),
    RESERVATION_ALREADY_RELEASED("订单 {0} 的库存已经释放，不能扣减"),
    ;

    private final String message;
}
