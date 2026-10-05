package com.example.mall.inventory.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.inventory.domain.model.SkuId;
import com.example.mall.inventory.domain.model.Stock;
import com.example.mall.inventory.infrastructure.orm.po.StockPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持久化对象转库存聚合。
 */
@Component
@EnhancedMapper(source = StockPO.class, target = Stock.class, description = "StockPO -> Stock")
public class StockEntityConverter implements ObjectMapper<StockPO, Stock> {

    @Override
    public Stock map(StockPO source) {
        return Stock.restore(SkuId.of(source.getSkuId()), source.getOnHand(), source.getReserved(), source.getVersion());
    }

    @Override
    public List<Stock> map(List<StockPO> sources) {
        return sources.stream().map(this::map).toList();
    }
}
