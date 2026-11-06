package com.example.inventory.application.handler;

import com.ddk.projection.starter.Projections;
import com.example.inventory.domain.acl.InventoryBoard;
import com.example.inventory.domain.event.StockReserved;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 库存变了，就标记「库存偏低」读模型里的这个商品。用普通的 {@code @EventListener}，标记和预留在同一个事务里提交。
 */
@Component
@RequiredArgsConstructor
public class StockLevelHandler {

    private final Projections projections;

    @EventListener
    public void on(StockReserved event) {
        projections.markDirty(InventoryBoard.LOW_STOCK, event.skuId());
    }
}
