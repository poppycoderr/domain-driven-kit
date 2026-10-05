package com.example.mall.inventory.application.service;

import com.ddk.core.exception.BusinessException;
import com.example.mall.inventory.application.command.ReserveStockCommand;
import com.example.mall.inventory.application.response.ReservationResponse;
import com.example.mall.inventory.application.response.StockResponse;
import com.example.mall.inventory.domain.acl.ReservationIdGenerator;
import com.example.mall.inventory.domain.acl.StockLock;
import com.example.mall.inventory.domain.acl.StockRepository;
import com.example.mall.inventory.domain.acl.StockReservationRepository;
import com.example.mall.inventory.domain.error.InventoryError;
import com.example.mall.inventory.domain.model.SkuId;
import com.example.mall.inventory.domain.model.Stock;
import com.example.mall.inventory.domain.model.StockReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * 库存应用服务。
 * <p>
 * 写操作的结构都是「先拿到涉及的 SKU 的锁，再在锁里开启并提交事务」，所以这里用 {@link TransactionTemplate} 而不是
 * {@code @Transactional}：事务必须整个落在锁的范围之内，锁释放时数据已经提交。
 * <p>
 * 预占、释放、扣减都可以重复执行：第 3 步接入消息之后，同一条消息可能被投递多次。
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockRepository stockRepository;

    private final StockReservationRepository reservationRepository;

    private final ReservationIdGenerator reservationIdGenerator;

    private final StockLock stockLock;

    private final TransactionTemplate transaction;

    public StockResponse get(String skuId) {
        return StockResponse.from(requireStock(SkuId.of(skuId)));
    }

    public List<ReservationResponse> reservationsOf(Long orderId) {
        return reservationRepository.findByOrder(orderId).stream().map(ReservationResponse::from).toList();
    }

    public StockResponse restock(String skuId, int quantity) {
        SkuId id = SkuId.of(skuId);
        return locked(List.of(id), () -> {
            Stock stock = requireStock(id);
            stock.restock(quantity);
            return StockResponse.from(stockRepository.update(stock));
        });
    }

    /**
     * 为订单的每一行预占库存，要么全部成功，要么一件都不占：任何一行库存不足，整个事务回滚。
     */
    public List<ReservationResponse> reserve(ReserveStockCommand command) {
        List<SkuId> skuIds = command.lines().stream().map(line -> SkuId.of(line.skuId())).toList();
        return locked(skuIds, () -> {
            List<SkuId> alreadyReserved = reservationRepository.findByOrder(command.orderId()).stream().map(StockReservation::skuId).toList();
            for (ReserveStockCommand.Line line : command.lines()) {
                SkuId skuId = SkuId.of(line.skuId());
                if (alreadyReserved.contains(skuId)) {
                    continue;
                }
                Stock stock = requireStock(skuId);
                stock.reserve(line.quantity());
                stockRepository.update(stock);
                reservationRepository.create(StockReservation.reserve(reservationIdGenerator.nextId(), command.orderId(), skuId, line.quantity()));
            }
            return reservationsOf(command.orderId());
        });
    }

    /**
     * 释放订单预占的库存。订单没有预占记录时什么都不做：订单可能在预占之前就取消了。
     */
    public List<ReservationResponse> release(Long orderId) {
        return settle(orderId, false);
    }

    /**
     * 扣减订单预占的库存。
     */
    public List<ReservationResponse> confirm(Long orderId) {
        if (reservationRepository.findByOrder(orderId).isEmpty()) {
            throw new BusinessException(InventoryError.RESERVATION_NOT_FOUND, orderId);
        }
        return settle(orderId, true);
    }

    private List<ReservationResponse> settle(Long orderId, boolean confirm) {
        List<SkuId> skuIds = reservationRepository.findByOrder(orderId).stream().map(StockReservation::skuId).toList();
        return locked(skuIds, () -> {
            for (StockReservation reservation : reservationRepository.findByOrder(orderId)) {
                boolean changed = confirm ? reservation.confirm() : reservation.release();
                if (!changed) {
                    continue;
                }
                Stock stock = requireStock(reservation.skuId());
                if (confirm) {
                    stock.deduct(reservation.quantity());
                } else {
                    stock.release(reservation.quantity());
                }
                stockRepository.update(stock);
                reservationRepository.update(reservation);
            }
            return reservationsOf(orderId);
        });
    }

    private <T> T locked(List<SkuId> skuIds, Supplier<T> action) {
        return stockLock.withSkus(skuIds, () -> transaction.execute(status -> action.get()));
    }

    private Stock requireStock(SkuId skuId) {
        return stockRepository.find(skuId).orElseThrow(() -> new BusinessException(InventoryError.STOCK_NOT_FOUND, skuId.value()));
    }
}
