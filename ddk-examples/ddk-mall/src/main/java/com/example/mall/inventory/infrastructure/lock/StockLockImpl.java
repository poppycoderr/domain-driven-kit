package com.example.mall.inventory.infrastructure.lock;

import com.ddk.concurrency.starter.AggregateLocks;
import com.ddk.core.exception.AggregateBusyException;
import com.example.mall.inventory.domain.acl.StockLock;
import com.example.mall.inventory.domain.model.SkuId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 库存锁的实现。
 * <p>
 * 有 Redis 时用 DDK 的 {@link AggregateLocks}，多个实例之间互斥。没有 Redis 时（默认 profile，为了不依赖任何外部组件就能启动）
 * 退回进程内的锁：只在单个实例内互斥，多实例部署不能用。两种实现都按 SKU 排序后依次加锁，避免两个订单互相等待对方持有的锁。
 */
@Slf4j
@Component
public class StockLockImpl implements StockLock {

    private static final String LOCK_TYPE = "sku";

    private static final long LOCAL_WAIT_SECONDS = 3;

    private final ObjectProvider<AggregateLocks> aggregateLocks;

    private final Map<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    public StockLockImpl(ObjectProvider<AggregateLocks> aggregateLocks) {
        this.aggregateLocks = aggregateLocks;
        if (aggregateLocks.getIfAvailable() == null) {
            log.warn("No Redis-backed locks available: stock is locked inside this JVM only. Do not run more than one instance.");
        }
    }

    @Override
    public <T> T withSkus(Collection<SkuId> skuIds, Supplier<T> action) {
        AggregateLocks distributed = aggregateLocks.getIfAvailable();
        if (distributed != null) {
            return distributed.executeAll(LOCK_TYPE, skuIds, action::get);
        }
        return locally(skuIds.stream().map(SkuId::value).distinct().sorted().toList(), action);
    }

    private <T> T locally(List<String> skus, Supplier<T> action) {
        Deque<ReentrantLock> held = new ArrayDeque<>();
        try {
            for (String sku : skus) {
                ReentrantLock lock = localLocks.computeIfAbsent(sku, key -> new ReentrantLock());
                if (!tryLock(lock)) {
                    throw new AggregateBusyException(LOCK_TYPE);
                }
                held.push(lock);
            }
            return action.get();
        } finally {
            held.forEach(ReentrantLock::unlock);
        }
    }

    private static boolean tryLock(ReentrantLock lock) {
        try {
            return lock.tryLock(LOCAL_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
