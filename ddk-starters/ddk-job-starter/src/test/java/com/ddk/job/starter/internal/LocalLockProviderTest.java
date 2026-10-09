package com.ddk.job.starter.internal;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("进程内的任务锁")
class LocalLockProviderTest {

    private final LocalLockProvider provider = new LocalLockProvider();

    @Test
    @DisplayName("同名任务同一时刻只有一个拿到锁，释放后可以再拿；不同名字互不影响")
    void oneHolderPerName() {
        Optional<SimpleLock> first = provider.lock(job("close-orders", Duration.ofMinutes(1), Duration.ZERO));

        assertThat(first).isPresent();
        assertThat(provider.lock(job("close-orders", Duration.ofMinutes(1), Duration.ZERO))).isEmpty();
        assertThat(provider.lock(job("send-reports", Duration.ofMinutes(1), Duration.ZERO))).isPresent();

        first.get().unlock();
        assertThat(provider.lock(job("close-orders", Duration.ofMinutes(1), Duration.ZERO))).isPresent();
    }

    @Test
    @DisplayName("lockAtLeastFor 之内释放了也拿不到；过了 lockAtMostFor 没释放的锁可以被接手")
    void honoursAtLeastAndAtMost() throws InterruptedException {
        provider.lock(job("short", Duration.ofMinutes(1), Duration.ofSeconds(30))).orElseThrow().unlock();
        assertThat(provider.lock(job("short", Duration.ofMinutes(1), Duration.ZERO))).as("still held for at-least-for").isEmpty();

        assertThat(provider.lock(job("crashed", Duration.ofMillis(50), Duration.ZERO))).isPresent();
        Thread.sleep(80);
        assertThat(provider.lock(job("crashed", Duration.ofMinutes(1), Duration.ZERO))).as("expired after at-most-for").isPresent();
    }

    @Test
    @DisplayName("超时的任务晚到的释放不会放掉下一轮的锁")
    void aLateUnlockDoesNotReleaseTheNextHolder() throws InterruptedException {
        SimpleLock overdue = provider.lock(job("slow", Duration.ofMillis(50), Duration.ZERO)).orElseThrow();
        Thread.sleep(80);
        assertThat(provider.lock(job("slow", Duration.ofMinutes(1), Duration.ZERO))).isPresent();

        overdue.unlock();

        assertThat(provider.lock(job("slow", Duration.ofMinutes(1), Duration.ZERO))).as("the second holder still has it").isEmpty();
    }

    private static LockConfiguration job(String name, Duration atMost, Duration atLeast) {
        return new LockConfiguration(Instant.now(), name, atMost, atLeast);
    }
}
