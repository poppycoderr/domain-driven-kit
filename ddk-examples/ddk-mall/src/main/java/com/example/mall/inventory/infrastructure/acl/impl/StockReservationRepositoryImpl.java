package com.example.mall.inventory.infrastructure.acl.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import com.example.mall.inventory.domain.acl.StockReservationRepository;
import com.example.mall.inventory.domain.model.ReservationId;
import com.example.mall.inventory.domain.model.StockReservation;
import com.example.mall.inventory.infrastructure.orm.mapper.StockReservationMapper;
import com.example.mall.inventory.infrastructure.orm.po.StockReservationPO;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 预占记录仓储实现。
 */
@Repository
public class StockReservationRepositoryImpl
        extends GenericRepositoryImpl<StockReservation, ReservationId, StockReservationPO, StockReservationMapper>
        implements StockReservationRepository {

    @Override
    public List<StockReservation> findByOrder(Long orderId) {
        return toEntity().map(getBaseMapper().selectList(Wrappers.lambdaQuery(StockReservationPO.class)
                .eq(StockReservationPO::getOrderId, orderId)
                .orderByAsc(StockReservationPO::getSkuId)));
    }
}
