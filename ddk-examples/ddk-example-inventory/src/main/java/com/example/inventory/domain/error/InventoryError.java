package com.example.inventory.domain.error;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存域错误码。
 */
@Getter
@AllArgsConstructor
public enum InventoryError implements ErrorCode {

    SKU_NOT_FOUND("商品不存在：{0}"),
    OUT_OF_STOCK("商品 {0} 库存不足，剩余 {1}"),
    INVALID_QUANTITY("预留数量必须大于 0：{0}"),
    ;

    private final String message;
}
