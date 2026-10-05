package com.example.mall.inventory.application.response;

import com.example.mall.inventory.domain.model.Stock;

/**
 * 库存对外响应。
 */
public record StockResponse(
        String skuId,

        int onHand,

        int reserved,

        int available
) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.id().value(), stock.onHand(), stock.reserved(), stock.available());
    }
}
