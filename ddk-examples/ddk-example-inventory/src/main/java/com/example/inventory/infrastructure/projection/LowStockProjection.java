package com.example.inventory.infrastructure.projection;

import com.ddk.projection.starter.Projection;
import com.example.inventory.domain.acl.InventoryBoard;
import com.example.inventory.domain.acl.SkuRepository;
import com.example.inventory.domain.model.SkuId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 「库存偏低」读模型：可售数量低于阈值的商品。每次刷新都按商品的当前状态重写这一行。
 */
@Component
@RequiredArgsConstructor
public class LowStockProjection implements Projection {

    private final SkuRepository skus;
    private final JdbcTemplate jdbc;

    @Value("${inventory.low-stock-threshold:5}")
    private int threshold;

    @Override
    public String name() {
        return InventoryBoard.LOW_STOCK;
    }

    @Override
    public void refresh(String id) {
        Long skuId = Long.valueOf(id);
        jdbc.update("DELETE FROM t_low_stock WHERE sku_id = ?", skuId);
        skus.find(SkuId.of(skuId)).filter(sku -> sku.available() < threshold).ifPresent(sku ->
                jdbc.update("INSERT INTO t_low_stock (sku_id, name, available) VALUES (?, ?, ?)", skuId, sku.name(), sku.available()));
    }

    @Override
    public void forEachId(Consumer<String> ids) {
        jdbc.query("SELECT id FROM t_sku", rs -> {
            ids.accept(rs.getString(1));
        });
    }
}
