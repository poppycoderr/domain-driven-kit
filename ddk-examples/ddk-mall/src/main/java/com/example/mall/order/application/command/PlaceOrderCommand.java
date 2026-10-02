package com.example.mall.order.application.command;

import java.util.List;

/**
 * 下单命令。只带 SKU 和数量，商品名称与单价由应用服务从商品目录取，不信任调用方传入的价格。
 */
public record PlaceOrderCommand(
        Long customerId,

        List<Line> lines
) {

    public record Line(
            String skuId,

            int quantity
    ) {
    }
}
