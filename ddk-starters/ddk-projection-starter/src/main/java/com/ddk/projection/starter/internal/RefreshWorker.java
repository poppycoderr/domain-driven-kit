package com.ddk.projection.starter.internal;

import com.ddk.projection.starter.Projection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 处理待刷新的标记：逐条调用读模型的 {@code refresh}，成功的删除标记，失败的推迟重试。
 * <p>
 * 用一个后台线程串行处理。事务提交后被唤醒一次，另外按固定间隔清扫，处理别的实例留下的、重启前没处理完的、以及到了重试时间的标记。
 * <p>
 * 多个实例共用同一张表。每条标记先领取再处理，同一时刻只有一个领取者；领取有租约，worker 停顿或宕机超过租约后别人可以接手。
 * 失去租约的 worker 恢复后仍可能把旧状态写进读模型，但它的确认会落空并重新标记这条数据，读模型随后收敛到最新状态，
 * 见 {@link PendingRefreshes}。
 */
public class RefreshWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RefreshWorker.class);

    private final Map<String, Projection> projections;

    private final PendingRefreshes pending;

    private final int batchSize;

    private final Duration sweepInterval;

    private final Duration retryDelay;

    private final Duration maxRetryDelay;

    private final Duration leaseTime;

    private final AtomicBoolean queued = new AtomicBoolean();

    private volatile ScheduledExecutorService executor = newExecutor();

    private volatile boolean running;

    public RefreshWorker(Collection<Projection> projections, PendingRefreshes pending, int batchSize, Duration sweepInterval,
            Duration retryDelay, Duration maxRetryDelay, Duration leaseTime) {
        this.leaseTime = leaseTime;
        this.projections = projections.stream().collect(Collectors.toMap(Projection::name, Function.identity(), (first, second) -> first));
        this.pending = pending;
        this.batchSize = batchSize;
        this.sweepInterval = sweepInterval;
        this.retryDelay = retryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    /**
     * 尽快处理一轮。已经有一轮在排队时不重复排队。
     */
    public void wake() {
        if (!running || !queued.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::sweep);
        } catch (RejectedExecutionException e) {
            queued.set(false);
        }
    }

    private void sweep() {
        queued.set(false);
        try {
            drain();
        } catch (RuntimeException e) {
            log.warn("Could not process pending projection refreshes, will try again at the next sweep", e);
        }
    }

    /**
     * 处理所有已经到期的标记，直到没有为止。
     *
     * @return 刷新成功的次数
     */
    public int drain() {
        int refreshed = 0;
        List<PendingRefreshes.Pending> batch;
        do {
            batch = pending.due(batchSize);
            for (PendingRefreshes.Pending item : batch) {
                if (refresh(item)) {
                    refreshed++;
                }
            }
        } while (batch.size() == batchSize && running);
        return refreshed;
    }

    private boolean refresh(PendingRefreshes.Pending item) {
        PendingRefreshes.Claim claim = pending.claim(item, leaseTime).orElse(null);
        if (claim == null) {
            return false;
        }
        Projection projection = projections.get(item.projection());
        try {
            if (projection == null) {
                throw new IllegalStateException("No projection named " + item.projection() + " in this application");
            }
            projection.refresh(item.id());
        } catch (RuntimeException e) {
            Duration delay = backoff(item.attempts());
            log.warn("Refreshing [{}] of projection [{}] failed (attempt {}), retrying in {}", item.id(), item.projection(),
                    item.attempts() + 1, delay, e);
            pending.failed(claim, delay, e.toString());
            return false;
        }
        if (!pending.done(claim)) {
            log.debug("[{}] of projection [{}] changed or was taken over during the refresh; marked again", item.id(), item.projection());
            return false;
        }
        return true;
    }

    /**
     * 每失败一次等待时间翻倍，到上限为止。
     */
    private Duration backoff(int attempts) {
        long factor = 1L << Math.min(attempts, 20);
        Duration delay = retryDelay.multipliedBy(factor);
        return delay.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : delay;
    }

    @Override
    public void start() {
        if (executor.isShutdown()) {
            executor = newExecutor();
        }
        running = true;
        executor.scheduleWithFixedDelay(this::sweep, sweepInterval.toMillis(), sweepInterval.toMillis(), TimeUnit.MILLISECONDS);
        wake();
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ddk-projection");
            thread.setDaemon(true);
            return thread;
        });
    }
}
