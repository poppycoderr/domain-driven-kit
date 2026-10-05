package com.example.mall.inventory.application.command;

import java.util.List;

/**
 * 为一个订单预占库存。
 */
public record ReserveStockCommand(
        Long orderId,

        List<Line> lines
) {

    public record Line(
            String skuId,

            int quantity
    ) {
    }
}
