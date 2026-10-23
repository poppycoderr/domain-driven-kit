package com.ddk.event.starter.internal;

import com.ddk.event.starter.config.DdkEventProperties;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时维护事件的持久化记录，全部通过 Spring Modulith 和 {@link IdempotentConsumer} 的公开接口完成，不直接改表：
 * <ol>
 *     <li>重投失败的发布：Modulith 把投递失败、以及陈旧检测判定卡住的发布标记为 FAILED，这里按批重投；</li>
 *     <li>删除超过保留期的已完成发布；</li>
 *     <li>删除超过保留期的已处理消息记录。</li>
 * </ol>
 * 每一步单独捕获异常，一步失败不影响其他步骤和下一轮。Modulith 默认的 JDBC 结构用条件更新认领每一条重投，旧结构
 * （{@code spring.modulith.events.jdbc.use-legacy-structure=true}）不认领；有 {@link MaintenanceLock} 时整轮在锁里执行，
 * 两种结构下都只有一个实例在维护。
 */
public class EventMaintenance implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EventMaintenance.class);

    private final @Nullable FailedEventPublications failed;

    private final @Nullable CompletedEventPublications completed;

    private final @Nullable IdempotentConsumer inbox;

    private final DdkEventProperties.Maintenance settings;

    private final MaintenanceLock lock;

    private @Nullable ScheduledExecutorService executor;

    public EventMaintenance(@Nullable FailedEventPublications failed, @Nullable CompletedEventPublications completed,
            @Nullable IdempotentConsumer inbox, DdkEventProperties.Maintenance settings, MaintenanceLock lock) {
        settings.validate();
        this.failed = failed;
        this.completed = completed;
        this.inbox = inbox;
        this.settings = settings;
        this.lock = lock;
    }

    /**
     * 执行一轮维护，拿不到锁时跳过。
     */
    public void runOnce() {
        lock.run(this::maintain);
    }

    private void maintain() {
        if (settings.isResubmitFailed() && failed != null) {
            step("resubmit failed publications", () -> failed.resubmit(ResubmissionOptions.defaults()
                    .withMinAge(settings.getResubmitMinAge())
                    .withBatchSize(settings.getResubmitBatchSize())));
        }
        if (settings.getDeleteCompletedAfter().isPositive() && completed != null) {
            step("delete completed publications", () -> completed.deletePublicationsOlderThan(settings.getDeleteCompletedAfter()));
        }
        if (settings.getInboxRetention().isPositive() && inbox != null) {
            step("purge processed messages", () -> {
                int purged = inbox.purgeOlderThan(settings.getInboxRetention());
                if (purged > 0) {
                    log.debug("Purged {} processed message records older than {}", purged, settings.getInboxRetention());
                }
            });
        }
    }

    private static void step(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Event maintenance could not {}, will try again in the next round", name, e);
        }
    }

    @Override
    public void start() {
        if (!lock.distributed()) {
            log.warn("ddk.event.maintenance runs on every instance because no ShedLock LockProvider is available: failed publications may be "
                    + "resubmitted by several instances at once, and consumers see duplicates. Add ddk-job-starter or a LockProvider bean, "
                    + "or enable maintenance on one instance only");
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ddk-event-maintenance");
            thread.setDaemon(true);
            return thread;
        });
        long interval = settings.getInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::runSafely, interval, interval, TimeUnit.MILLISECONDS);
        executor = scheduler;
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.warn("Event maintenance round failed", e);
        }
    }

    @Override
    public void stop() {
        ScheduledExecutorService scheduler = executor;
        if (scheduler != null) {
            scheduler.shutdown();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }
}
