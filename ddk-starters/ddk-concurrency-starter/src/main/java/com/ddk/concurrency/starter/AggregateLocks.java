package com.ddk.concurrency.starter;

import com.ddk.core.domain.Identifier;
import com.ddk.core.exception.AggregateBusyException;
import org.jspecify.annotations.Nullable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 按聚合加锁执行一段逻辑，是 {@link AggregateLock} 的编程式入口。
 * <p>
 * 锁名是「前缀 + lock: + 聚合类型 + : + 聚合标识」，同一个线程可以重入。要让锁覆盖事务的提交，在事务之外调用它。
 */
public class AggregateLocks {

    private static final Logger log = LoggerFactory.getLogger(AggregateLocks.class);

    private final RedissonClient redisson;

    private final String keyPrefix;

    private final Duration defaultWaitTime;

    private final @Nullable Duration defaultLeaseTime;

    public AggregateLocks(RedissonClient redisson, String keyPrefix, Duration defaultWaitTime, @Nullable Duration defaultLeaseTime) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix;
        this.defaultWaitTime = defaultWaitTime;
        this.defaultLeaseTime = defaultLeaseTime;
    }

    /**
     * 用默认的等待与持有时间加锁执行。
     *
     * @throws AggregateBusyException 等待时间内没有拿到锁
     */
    public <T, E extends Throwable> T execute(String type, Object id, Action<T, E> action) throws E {
        return execute(type, id, defaultWaitTime, defaultLeaseTime, action);
    }

    /**
     * @param leaseTime 持有锁的最长时间；为 {@code null} 时由 Redisson 的看门狗自动续期，直到执行结束
     * @throws AggregateBusyException 等待时间内没有拿到锁
     */
    public <T, E extends Throwable> T execute(String type, Object id, Duration waitTime, @Nullable Duration leaseTime, Action<T, E> action)
            throws E {
        String name = lockName(type, id);
        RLock lock = redisson.getLock(name);
        if (!acquire(lock, waitTime, leaseTime)) {
            throw new AggregateBusyException(type);
        }
        try {
            return action.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            } else {
                log.warn("Lock [{}] expired before the operation finished; raise the lease time or leave it to the watchdog", name);
            }
        }
    }

    /**
     * 同时独占同一类型的多个聚合，用默认的等待与持有时间。
     * <p>
     * 锁按名称排序后依次获取。所有调用方都按同一个顺序加锁，两个操作各持有一把、互相等对方那一把的死锁就不会出现；
     * 自己写嵌套的 {@code execute} 时顺序取决于调用方传入的顺序，做不到这一点。等待时间对每一把锁分别计算。
     *
     * @throws AggregateBusyException 其中任何一把锁在等待时间内没有拿到；已经拿到的会被释放
     */
    public <T, E extends Throwable> T executeAll(String type, Collection<?> ids, Action<T, E> action) throws E {
        List<Object> ordered = ids.stream().distinct().sorted(Comparator.comparing(id -> lockName(type, id))).map(id -> (Object) id).toList();
        return executeNested(type, ordered, 0, action);
    }

    private <T, E extends Throwable> T executeNested(String type, List<Object> ids, int index, Action<T, E> action) throws E {
        if (index == ids.size()) {
            return action.run();
        }
        return execute(type, ids.get(index), () -> executeNested(type, ids, index + 1, action));
    }

    public String lockName(String type, Object id) {
        Object raw = id instanceof Identifier<?> identifier ? identifier.value() : id;
        return keyPrefix + "lock:" + type + ":" + raw;
    }

    private static boolean acquire(RLock lock, Duration waitTime, @Nullable Duration leaseTime) {
        try {
            return leaseTime == null
                    ? lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS)
                    : lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 持锁期间执行的逻辑。
     */
    @FunctionalInterface
    public interface Action<T, E extends Throwable> {

        T run() throws E;
    }
}
