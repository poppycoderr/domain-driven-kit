package com.example.inventory.application.response;

import com.example.inventory.domain.acl.InventoryBoard;

/**
 * 「库存偏低」读模型里的一行。
 */
public record LowStockResponse(
        Long skuId,
        String name,
        int available
) {

    public static LowStockResponse from(InventoryBoard.LowStock row) {
        return new LowStockResponse(row.skuId(), row.name(), row.available());
    }
}
