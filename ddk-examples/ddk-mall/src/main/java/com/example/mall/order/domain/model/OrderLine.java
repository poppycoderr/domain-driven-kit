package com.example.mall.order.domain.model;

import com.ddk.core.exception.BusinessException;
import com.example.mall.order.domain.error.OrderError;

import java.util.Objects;

/**
 * 订单行。商品名称和单价是下单那一刻的快照，之后商品改名、调价都不影响已有订单。
 *
 * @param skuId       商品 SKU
 * @param productName 下单时的商品名称
 * @param unitPrice   下单时的单价
 * @param quantity    数量
 */
public record OrderLine(
        String skuId,

        String productName,

        Money unitPrice,

        int quantity
) {

    public OrderLine {
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity <= 0) {
            throw new BusinessException(OrderError.INVALID_QUANTITY, skuId, quantity);
        }
    }

    public Money subtotal() {
        return unitPrice.times(quantity);
    }
}
