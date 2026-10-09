package com.ddk.projection.starter;

import com.ddk.core.domain.Identifier;
import com.ddk.projection.starter.internal.PendingRefreshes;
import com.ddk.projection.starter.internal.RefreshWorker;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 读模型的入口：标记哪一条数据变了，以及重建整个读模型。
 * <p>
 * 标记写进一张待刷新表。在事务里调用时，标记和业务修改一起提交、一起回滚：数据改了而读模型没有跟上的情况不会因为进程崩溃而出现。
 * 提交之后立即刷新，失败的稍后重试，另有定时清扫处理遗留的标记。同一条数据在刷新之前被标记多次，只刷新一次。
 */
public class Projections {

    private final Map<String, Projection> projections;

    private final PendingRefreshes pending;

    private final RefreshWorker worker;

    /**
     * @throws IllegalStateException 两个读模型用了同一个名称
     */
    public Projections(Collection<Projection> projections, PendingRefreshes pending, RefreshWorker worker) {
        this.projections = projections.stream().collect(Collectors.toUnmodifiableMap(Projection::name, Function.identity(), (first, second) -> {
            throw new IllegalStateException("Two projections are named " + first.name() + ": "
                    + first.getClass().getName() + " and " + second.getClass().getName());
        }));
        this.pending = pending;
        this.worker = worker;
    }

    /**
     * 标记读模型里的这一条需要刷新。通常在领域事件的监听器里调用，事件里带着聚合的标识。
     *
     * @param id 聚合的标识；{@link Identifier} 取它的原始值，其他类型取 {@code String.valueOf}
     * @throws IllegalArgumentException 没有这个名称的读模型
     */
    public void markDirty(String projection, Object id) {
        require(projection);
        pending.mark(projection, text(id));
        refreshSoon();
    }

    /**
     * 重建读模型：把写模型里的每一个标识都标记为需要刷新，随后由后台逐个刷新。方法返回时标记已经写完，刷新可能还在进行，
     * 用 {@link #pending} 查看剩余数量。重建期间进程重启不影响结果，没处理完的标记仍在表里。
     * <p>
     * 重建不会删除读模型里多出来的数据（写模型里已经没有的那些）；需要清理时先清空读模型再重建。
     *
     * @return 标记的数量
     * @throws IllegalArgumentException      没有这个名称的读模型
     * @throws UnsupportedOperationException 这个读模型没有实现 {@link Projection#forEachId}
     */
    public long rebuild(String projection) {
        AtomicLong marked = new AtomicLong();
        require(projection).forEachId(id -> {
            pending.mark(projection, id);
            marked.incrementAndGet();
        });
        refreshSoon();
        return marked.get();
    }

    /**
     * 这个读模型还有多少条等待刷新，包括失败后等待重试的。
     */
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
