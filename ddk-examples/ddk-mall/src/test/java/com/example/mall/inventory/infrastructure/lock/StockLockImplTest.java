package com.example.mall.inventory.infrastructure.lock;

import com.ddk.concurrency.starter.AggregateLocks;
import com.example.mall.inventory.domain.model.SkuId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("没有 Redis 时的进程内库存锁")
class StockLockImplTest {

    private final StockLockImpl locks = new StockLockImpl(new StaticListableBeanFactory().getBeanProvider(AggregateLocks.class));

    @Test
    @DisplayName("等锁时被中断按取消处理：抛 CancellationException，中断标志保留，操作没有执行")
    void interruptionWhileWaitingIsCancellation() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> locks.withSkus(List.of(SkuId.of("A")), () -> {
            held.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        AtomicInteger ran = new AtomicInteger();
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();

        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                locks.withSkus(List.of(SkuId.of("A")), ran::incrementAndGet);
                failure.complete(null);
            } catch (Throwable e) {
                interruptedAfter.set(Thread.currentThread().isInterrupted());
                failure.complete(e);
            }
        });
        Thread.sleep(200);
        waiter.interrupt();

        assertThat(failure.get(10, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
        assertThat(interruptedAfter).isTrue();
        assertThat(ran).hasValue(0);
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
    }
}
