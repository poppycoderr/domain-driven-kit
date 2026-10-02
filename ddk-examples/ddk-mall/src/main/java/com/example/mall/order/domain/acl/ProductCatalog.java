package com.example.mall.order.domain.acl;

import com.example.mall.order.domain.model.Money;

import java.util.Optional;

/**
 * 商品目录端口。订单上下文只需要下单那一刻的商品名称和单价，不关心商品是怎么管理的。
 */
public interface ProductCatalog {

    Optional<Product> find(String skuId);

    /**
     * 订单上下文眼里的商品。
     */
    record Product(
            String skuId,

            String name,

            Money unitPrice
    ) {
    }
}
