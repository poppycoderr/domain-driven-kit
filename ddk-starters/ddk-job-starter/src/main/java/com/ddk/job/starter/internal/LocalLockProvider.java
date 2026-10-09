package com.ddk.job.starter.internal;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把任务锁放在当前进程的内存里。
 * <p>
 * 用于没有 Redis 的环境，例如本地开发：任务照常执行，同一个进程里同名任务不会重叠，{@code lockAtMostFor} 与
 * {@code lockAtLeastFor} 的含义不变。它不跨进程，多个实例各跑各的，所以只能用在单实例上。
 */
public final class LocalLockProvider implements LockProvider {

    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    @Override
    public Optional<SimpleLock> lock(LockConfiguration configuration) {
        Instant now = Instant.now();
        Instant until = configuration.getLockAtMostUntil();
        boolean[] acquired = {false};
        lockedUntil.compute(configuration.getName(), (name, current) -> {
            if (current != null && current.isAfter(now)) {
                return current;
            }
            acquired[0] = true;
            return until;
        });
        if (!acquired[0]) {
            return Optional.empty();
        }
        return Optional.of(() -> release(configuration, until));
    }

    /**
     * 只释放自己加的那一次：任务超过了 lockAtMostFor 时锁可能已经被下一轮拿走，不能把别人的锁放掉。
     */
    private void release(LockConfiguration configuration, Instant until) {
        Instant now = Instant.now();
        Instant atLeastUntil = configuration.getLockAtLeastUntil();
        lockedUntil.computeIfPresent(configuration.getName(), (name, current) -> {
            if (!current.equals(until)) {
                return current;
            }
            return atLeastUntil.isAfter(now) ? atLeastUntil : null;
        });
    }
}
