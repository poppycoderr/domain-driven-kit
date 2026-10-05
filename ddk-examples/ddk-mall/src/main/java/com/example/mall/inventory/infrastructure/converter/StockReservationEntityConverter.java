package com.example.mall.inventory.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.inventory.domain.model.ReservationId;
import com.example.mall.inventory.domain.model.ReservationStatus;
import com.example.mall.inventory.domain.model.SkuId;
import com.example.mall.inventory.domain.model.StockReservation;
import com.example.mall.inventory.infrastructure.orm.po.StockReservationPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持久化对象转预占记录。
 */
@Component
@EnhancedMapper(source = StockReservationPO.class, target = StockReservation.class, description = "StockReservationPO -> StockReservation")
public class StockReservationEntityConverter implements ObjectMapper<StockReservationPO, StockReservation> {

    @Override
    public StockReservation map(StockReservationPO source) {
        return StockReservation.restore(ReservationId.of(source.getId()), source.getOrderId(), SkuId.of(source.getSkuId()),
                source.getQuantity(), ReservationStatus.valueOf(source.getStatus()), source.getVersion());
    }

    @Override
    public List<StockReservation> map(List<StockReservationPO> sources) {
        return sources.stream().map(this::map).toList();
    }
}
