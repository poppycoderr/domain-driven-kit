package com.example.inventory.application.service;

import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.Idempotent;
import com.ddk.concurrency.starter.RateLimit;
import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.exception.BusinessException;
import com.example.inventory.application.command.ReserveStockCommand;
import com.example.inventory.application.response.LowStockResponse;
import com.example.inventory.application.response.SkuResponse;
import com.example.inventory.domain.acl.InventoryBoard;
import com.example.inventory.domain.acl.SkuRepository;
import com.example.inventory.domain.error.InventoryError;
import com.example.inventory.domain.model.Sku;
import com.example.inventory.domain.model.SkuId;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库存的用例：查询走两级缓存，预留按商品加锁、按请求号防重复，可用量检查按商品限流。
 * <p>
 * 缓存里的 {@link SkuResponse} 由 Redis 的 JSON 序列化读写，原生镜像里要登记它的反射。
 */
@Service
@RequiredArgsConstructor
@RegisterReflectionForBinding(SkuResponse.class)
public class InventoryService {

    private final SkuRepository skus;
    private final InventoryBoard board;
    private final DomainEventPublisher events;

    @Cacheable(cacheNames = "skus", key = "#skuId")
    public SkuResponse find(Long skuId) {
        return SkuResponse.from(load(skuId));
    }

    @RateLimit(limit = 3, period = "1m", key = "#skuId")
    public SkuResponse checkAvailability(Long skuId) {
        return SkuResponse.from(load(skuId));
    }

    @Transactional
    @AggregateLock(type = "sku", id = "#command.skuId()")
    @Idempotent(key = "#command.requestId()")
    @CacheEvict(cacheNames = "skus", key = "#command.skuId()")
    public SkuResponse reserve(ReserveStockCommand command) {
        Sku sku = load(command.skuId());
        sku.reserve(command.requestId(), command.quantity());
        skus.update(sku);
        sku.drainDomainEvents().forEach(events::publish);
        return SkuResponse.from(sku);
    }

    public List<LowStockResponse> lowStock() {
        return board.lowStock().stream().map(LowStockResponse::from).toList();
    }

    public List<String> reservationsOf(Long skuId) {
        return board.reservationsOf(skuId);
    }

    private Sku load(Long skuId) {
        return skus.find(SkuId.of(skuId)).orElseThrow(() -> new BusinessException(InventoryError.SKU_NOT_FOUND, skuId));
    }
}
