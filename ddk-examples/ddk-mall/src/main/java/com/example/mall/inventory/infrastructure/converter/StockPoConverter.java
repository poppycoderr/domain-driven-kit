package com.example.mall.inventory.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.inventory.domain.model.Stock;
import com.example.mall.inventory.infrastructure.orm.po.StockPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存聚合转持久化对象。
 */
@Component
@EnhancedMapper(source = Stock.class, target = StockPO.class, description = "Stock -> StockPO")
public class StockPoConverter implements ObjectMapper<Stock, StockPO> {

    @Override
    public StockPO map(Stock source) {
        StockPO po = new StockPO();
        po.setSkuId(source.id().value());
        po.setOnHand(source.onHand());
        po.setReserved(source.reserved());
        po.setVersion(source.version());
        return po;
    }

    @Override
    public List<StockPO> map(List<Stock> sources) {
        return sources.stream().map(this::map).toList();
    }
}
