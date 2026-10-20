package com.ddk.projection.starter.internal;

import com.ddk.core.domain.Identifier;
import com.ddk.projection.starter.Projection;
import com.ddk.projection.starter.Projections;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link Projections} 的实现：标记写进待刷新表，提交之后唤醒 {@link RefreshWorker}。
 */
public class JdbcProjections implements Projections {

    private final Map<String, Projection> projections;

    private final PendingRefreshes pending;

    private final RefreshWorker worker;

    /**
     * @throws IllegalStateException 两个读模型用了同一个名称
     */
    public JdbcProjections(Collection<Projection> projections, PendingRefreshes pending, RefreshWorker worker) {
        this.projections = projections.stream().collect(Collectors.toUnmodifiableMap(Projection::name, Function.identity(), (first, second) -> {
            throw new IllegalStateException("Two projections are named " + first.name() + ": "
                    + first.getClass().getName() + " and " + second.getClass().getName());
        }));
        this.pending = pending;
        this.worker = worker;
    }

    @Override
    public void markDirty(String projection, Object id) {
        require(projection);
        pending.mark(projection, text(id));
        refreshSoon();
    }

    @Override
    public long rebuild(String projection) {
        AtomicLong marked = new AtomicLong();
        require(projection).forEachId(id -> {
            pending.mark(projection, id);
            marked.incrementAndGet();
        });
        refreshSoon();
        return marked.get();
    }

    @Override
    public long pending(String projection) {
        require(projection);
        return pending.count(projection);
    }

    private Projection require(String name) {
        Projection projection = projections.get(name);
        if (projection == null) {
            throw new IllegalArgumentException("No projection named " + name + ". Known projections: " + projections.keySet());
        }
        return projection;
    }

    /**
     * 在事务里时等提交之后再刷新：提交之前刷新读到的还是旧数据。
     */
    private void refreshSoon() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    worker.wake();
                }
            });
        } else {
            worker.wake();
        }
    }

    private static String text(Object id) {
        Object raw = id instanceof Identifier<?> identifier ? identifier.value() : id;
        return String.valueOf(raw);
    }
}
