package com.example.mall.inventory.infrastructure.acl.impl;

import com.ddk.mybatis.repository.GenericRepositoryImpl;
import com.example.mall.inventory.domain.acl.StockRepository;
import com.example.mall.inventory.domain.model.SkuId;
import com.example.mall.inventory.domain.model.Stock;
import com.example.mall.inventory.infrastructure.orm.mapper.StockMapper;
import com.example.mall.inventory.infrastructure.orm.po.StockPO;
import org.springframework.stereotype.Repository;

/**
 * 库存仓储实现。
 */
@Repository
public class StockRepositoryImpl extends GenericRepositoryImpl<Stock, SkuId, StockPO, StockMapper> implements StockRepository {
}
