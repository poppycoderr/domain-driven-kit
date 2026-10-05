package com.example.mall.inventory.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.mall.inventory.domain.model.ReservationId;
import com.example.mall.inventory.domain.model.StockReservation;

import java.util.List;

/**
 * 预占记录仓储。
 */
public interface StockReservationRepository extends GenericRepository<StockReservation, ReservationId> {

    /**
     * 一个订单的全部预占记录，按 SKU 排序。
     */
    List<StockReservation> findByOrder(Long orderId);
}
