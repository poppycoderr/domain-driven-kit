package com.example.inventory.domain.acl;

import java.util.List;

/**
 * 读模型的查询端口：库存偏低的商品，以及一个商品的预留记录。
 */
public interface InventoryBoard {

    /** 「库存偏低」读模型的名字。 */
    String LOW_STOCK = "low-stock";

    List<LowStock> lowStock();

    List<String> reservationsOf(Long skuId);

    record LowStock(
            Long skuId,
            String name,
            int available
    ) {
    }
}
