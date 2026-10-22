package com.ddk.job.starter;

import com.ddk.job.starter.config.DdkJobAutoConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 开启虚拟线程后，Spring Boot 的调度器给每次执行一个新线程：固定频率的任务上一次没跑完，下一次就可能开始。
 * 平台线程的默认调度器只有一个线程，不会出现这种重叠。加了 {@code @SchedulerLock} 的任务由锁挡住重叠的那一次。
 */
@DisplayName("虚拟线程上的定时任务")
class VirtualThreadSchedulingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class, DdkJobAutoConfiguration.class))
            .withUserConfiguration(Jobs.class)
            .withPropertyValues("ddk.job.lock.store=local");

    @Test
    @DisplayName("固定频率的任务在虚拟线程上会重叠；加锁的任务同一时间只有一次在执行")
    void overlappingRunsNeedTheLock() {
        runner.withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            ReportJobs jobs = context.getBean(ReportJobs.class);
            await().atMost(10, TimeUnit.SECONDS).until(() -> jobs.unlocked().finished.get() >= 3 && jobs.locked().finished.get() >= 2);

            assertThat(jobs.unlocked().maxConcurrent).hasValueGreaterThan(1);
            assertThat(jobs.locked().maxConcurrent).hasValue(1);
            assertThat(jobs.locked().onVirtualThread).isTrue();
        });
    }

    @Test
    @DisplayName("没开虚拟线程时调度器只有一个线程，不加锁的任务也不会重叠")
    void platformSchedulerDoesNotOverlap() {
        runner.run(context -> {
            ReportJobs jobs = context.getBean(ReportJobs.class);
            await().atMost(10, TimeUnit.SECONDS).until(() -> jobs.unlocked().finished.get() >= 3);

            assertThat(jobs.unlocked().maxConcurrent).hasValue(1);
        });
    }

    static final class Runs {

        final AtomicInteger running = new AtomicInteger();

        final AtomicInteger maxConcurrent = new AtomicInteger();

        final AtomicInteger finished = new AtomicInteger();

        final AtomicBoolean onVirtualThread = new AtomicBoolean();

        void run() throws InterruptedException {
            maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
            onVirtualThread.set(Thread.currentThread().isVirtual());
            try {
                Thread.sleep(300);
            } finally {
                running.decrementAndGet();
                finished.incrementAndGet();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Jobs {

        @Bean
        ReportJobs reportJobs() {
            return new ReportJobs();
        }
    }

    /**
     * 计数放在每个应用上下文自己的 Bean 里：上下文关闭后，已经交给虚拟线程的执行还会跑完，不能影响下一个测试。
     */
    static class ReportJobs {

        private final Runs locked = new Runs();

        private final Runs unlocked = new Runs();

        // 测试拿到的是 ShedLock 的代理，要通过方法读取，不能直接读字段
        public Runs locked() {
            return locked;
        }

        public Runs unlocked() {
            return unlocked;
        }

        @Scheduled(fixedRate = 50)
        @SchedulerLock(name = "rebuildReport", lockAtMostFor = "10s")
        public void rebuildReport() throws InterruptedException {
            locked.run();
        }

        @Scheduled(fixedRate = 50)
        public void refreshLocalCache() throws InterruptedException {
            unlocked.run();
        }
    }
}
