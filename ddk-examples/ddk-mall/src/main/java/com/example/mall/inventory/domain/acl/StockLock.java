package com.example.mall.inventory.domain.acl;

import com.example.mall.inventory.domain.model.SkuId;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 按 SKU 独占库存的端口。
 * <p>
 * 库存是热点数据：只靠乐观锁，同一个 SKU 上的并发预占大多会在提交时冲突，白做一遍。加锁让它们排队，
 * 每个操作开始时读到的都是上一个操作提交后的数量。乐观锁仍然保留，锁失效时由它兜底。
 */
public interface StockLock {

    /**
     * 独占这些 SKU 期间执行操作。操作应当在内部开启并提交事务，锁才能覆盖到提交。
     */
    <T> T withSkus(Collection<SkuId> skuIds, Supplier<T> action);
}
