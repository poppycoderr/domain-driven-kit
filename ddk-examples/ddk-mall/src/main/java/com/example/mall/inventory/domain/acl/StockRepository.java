package com.example.mall.inventory.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.mall.inventory.domain.model.SkuId;
import com.example.mall.inventory.domain.model.Stock;

/**
 * 库存仓储。
 */
public interface StockRepository extends GenericRepository<Stock, SkuId> {
}
