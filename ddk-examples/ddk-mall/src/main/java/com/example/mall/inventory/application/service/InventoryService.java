package com.example.mall.inventory.application.service;

import com.ddk.core.exception.BusinessException;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import com.example.mall.inventory.application.command.ReserveStockCommand;
import com.example.mall.inventory.application.integration.StockReservationRejectedMessage;
import com.example.mall.inventory.application.integration.StockReservedMessage;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 库存应用服务。
 * <p>
 * 写操作的结构都是「先拿到涉及的 SKU 的锁，再在锁里开启并提交事务」，所以这里用 {@link TransactionTemplate} 而不是
 * {@code @Transactional}：事务必须整个落在锁的范围之内，锁释放时数据已经提交。
 * <p>
 * 预占、释放、扣减都可以重复执行：它们由消息驱动，而同一条消息可能被投递多次。
 * <p>
 * 库存的查询结果放在缓存里，写操作在事务提交之后让涉及的 SKU 失效。失效必须在提交之后：提交之前失效的话，
 * 另一个请求可能立刻把旧值重新读进缓存。提交和失效之间仍有一个极短的窗口会读到旧值，缓存的过期时间是它的上限。
 * 预占用的是数据库里的库存，不读缓存，所以缓存只影响展示，不影响是否超卖。
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final String STOCK_CACHE = "stock";

    private static final String ORDER_PLACED_CONSUMER = "inventory.order-placed";

    private final StockRepository stockRepository;

    private final StockReservationRepository reservationRepository;

    private final ReservationIdGenerator reservationIdGenerator;

    private final StockLock stockLock;

    private final TransactionTemplate transaction;

    private final IdempotentConsumer idempotentConsumer;

    private final ApplicationEventPublisher publisher;

    private final CacheManager cacheManager;

    @Cacheable(cacheNames = STOCK_CACHE, key = "#skuId")
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
            reserveLines(command);
            return reservationsOf(command.orderId());
        });
    }

    private void reserveLines(ReserveStockCommand command) {
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
    }

    /**
     * 响应「订单已下单」：预占库存，并把结果告诉订单上下文。
     * <p>
     * 结果消息和预占在同一个事务里登记，所以不会出现「库存占了、订单却不知道」。同一条消息被投递两次时，
     * {@link IdempotentConsumer} 让第二次什么都不做，订单上下文也就不会收到两份结果。去重登记写在锁和事务的里面：
     * 它必须和业务修改一起提交、一起回滚，而事务又必须整个落在锁的范围内。
     * <p>
     * 库存不足是业务上的正常结果，不是消费失败：这里发出「预占失败」并正常返回，消息不会被重投。
     */
    public void reserveForOrder(String messageId, ReserveStockCommand command) {
        List<SkuId> skuIds = command.lines().stream().map(line -> SkuId.of(line.skuId())).toList();
        try {
            locked(skuIds, () -> idempotentConsumer.handle(ORDER_PLACED_CONSUMER, messageId, () -> {
                reserveLines(command);
                publisher.publishEvent(new StockReservedMessage(UUID.randomUUID().toString(), command.orderId()));
            }));
        } catch (BusinessException rejection) {
            if (rejection.getErrorCode() != InventoryError.INSUFFICIENT_STOCK && rejection.getErrorCode() != InventoryError.STOCK_NOT_FOUND) {
                throw rejection;
            }
            transaction.executeWithoutResult(status -> idempotentConsumer.handle(ORDER_PLACED_CONSUMER, messageId, () ->
                    publisher.publishEvent(new StockReservationRejectedMessage(UUID.randomUUID().toString(), command.orderId(), rejection.getMessage()))));
        }
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

    /**
     * 锁住这些 SKU，在锁里开启并提交事务，提交（或回滚）之后让它们的缓存失效。
     */
    private <T> T locked(List<SkuId> skuIds, Supplier<T> action) {
        return stockLock.withSkus(skuIds, () -> {
            try {
                return transaction.execute(status -> action.get());
            } finally {
                Cache cache = cacheManager.getCache(STOCK_CACHE);
                if (cache != null) {
                    skuIds.forEach(skuId -> cache.evict(skuId.value()));
                }
            }
        });
    }

    private Stock requireStock(SkuId skuId) {
        return stockRepository.find(skuId).orElseThrow(() -> new BusinessException(InventoryError.STOCK_NOT_FOUND, skuId.value()));
    }
}
