package com.ddk.core.context;

import io.micrometer.context.ContextSnapshotFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OperatorContext 的跨线程传播")
class OperatorContextPropagationTest {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        OperatorContext.clear();
    }

    @Test
    @DisplayName("Spring 的 ContextPropagatingTaskDecorator 把提交时的操作者带到执行线程，结束后线程上不留操作者")
    void theTaskDecoratorCarriesTheOperator() throws Exception {
        ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();
        Operator alice = Operator.of("alice", "tenant-1");

        Runnable task = OperatorContext.callAs(alice, () -> decorator.decorate(this::record));
        pool.submit(task).get(5, TimeUnit.SECONDS);

        assertThat(seen).contains(alice);
        assertThat(CompletableFuture.supplyAsync(OperatorContext::current, pool).get(5, TimeUnit.SECONDS))
                .as("the pool thread is clean afterwards").isEmpty();
    }

    @Test
    @DisplayName("执行线程原来有操作者时，任务里用提交方的，任务结束后恢复执行线程原来的")
    void theExecutingThreadGetsItsOwnOperatorBack() throws Exception {
        Operator worker = Operator.of("worker");
        Operator alice = Operator.of("alice", "tenant-1");
        pool.submit(() -> OperatorContext.set(worker)).get(5, TimeUnit.SECONDS);

        Runnable task = OperatorContext.callAs(alice, () -> ContextSnapshotFactory.builder().build().captureAll().wrap((Runnable) this::record));
        pool.submit(task).get(5, TimeUnit.SECONDS);

        assertThat(seen).contains(alice);
        assertThat(CompletableFuture.supplyAsync(OperatorContext::current, pool).get(5, TimeUnit.SECONDS)).contains(worker);
    }

    private volatile Optional<Operator> seen = Optional.empty();

    private void record() {
        seen = OperatorContext.current();
    }
}
