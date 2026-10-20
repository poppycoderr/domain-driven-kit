package com.ddk.event.starter.internal;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;

/**
 * 用应用里的 ShedLock {@link LockProvider} 互斥（ddk-job-starter 提供的就是一个）。{@code LockProvider} 可能由别的自动配置在之后注册，
 * 所以第一次使用时才去取；没有时退化为不加锁。
 */
public class ShedLockMaintenanceLock implements MaintenanceLock {

    static final String LOCK_NAME = "ddk-event-maintenance";

    private final ObjectProvider<LockProvider> providers;

    private final Duration interval;

    private volatile @Nullable DefaultLockingTaskExecutor executor;

    public ShedLockMaintenanceLock(ObjectProvider<LockProvider> providers, Duration interval) {
        this.providers = providers;
        this.interval = interval;
    }

    /**
     * 锁最多持有十个间隔（实例在维护中途宕机时，之后由别的实例接手），最少持有半个间隔（时钟稍有偏差的实例不会紧接着再做一轮）。
     */
    @Override
    public void run(Runnable task) {
        DefaultLockingTaskExecutor current = executor();
        if (current == null) {
            task.run();
            return;
        }
        current.executeWithLock(task, new LockConfiguration(Instant.now(), LOCK_NAME, interval.multipliedBy(10), interval.dividedBy(2)));
    }

    @Override
    public boolean distributed() {
        return executor() != null;
    }

    private @Nullable DefaultLockingTaskExecutor executor() {
        DefaultLockingTaskExecutor current = executor;
        if (current == null) {
            LockProvider provider = providers.getIfAvailable();
            if (provider != null) {
                current = new DefaultLockingTaskExecutor(provider);
                executor = current;
            }
        }
        return current;
    }
}
