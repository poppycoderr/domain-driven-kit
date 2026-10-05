package com.example.mall.inventory.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.inventory.domain.model.StockReservation;
import com.example.mall.inventory.infrastructure.orm.po.StockReservationPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预占记录转持久化对象。
 */
@Component
@EnhancedMapper(source = StockReservation.class, target = StockReservationPO.class, description = "StockReservation -> StockReservationPO")
public class StockReservationPoConverter implements ObjectMapper<StockReservation, StockReservationPO> {

    @Override
    public StockReservationPO map(StockReservation source) {
        StockReservationPO po = new StockReservationPO();
        po.setId(source.id().value());
        po.setOrderId(source.orderId());
        po.setSkuId(source.skuId().value());
        po.setQuantity(source.quantity());
        po.setStatus(source.status().name());
        po.setVersion(source.version());
        return po;
    }

    @Override
    public List<StockReservationPO> map(List<StockReservation> sources) {
        return sources.stream().map(this::map).toList();
    }
}
