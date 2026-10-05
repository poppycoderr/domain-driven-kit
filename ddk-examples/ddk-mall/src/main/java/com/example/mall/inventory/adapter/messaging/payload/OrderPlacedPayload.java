package com.example.mall.inventory.adapter.messaging.payload;

import java.util.List;

/**
 * 库存上下文眼里的「订单已下单」消息，只声明用得到的字段。
 */
public record OrderPlacedPayload(
        Long orderId,

        List<Line> lines
) {

    public record Line(
            String skuId,

            int quantity
    ) {
    }
}
