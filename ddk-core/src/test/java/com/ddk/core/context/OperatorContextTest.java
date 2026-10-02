package com.ddk.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OperatorContext：当前操作者")
class OperatorContextTest {

    private final Operator alice = Operator.of("1001", "t-1");

    private final Operator system = new Operator("system", "定时任务", null);

    @AfterEach
    void clear() {
        OperatorContext.clear();
    }

    @Test
    @DisplayName("runAs 期间可见，结束后恢复成之前的值，嵌套也一样")
    void runAsRestoresThePreviousOperator() {
        assertThat(OperatorContext.current()).isEmpty();

        OperatorContext.runAs(alice, () -> {
            assertThat(OperatorContext.required()).isEqualTo(alice);
            OperatorContext.runAs(system, () -> assertThat(OperatorContext.required().name()).isEqualTo("定时任务"));
            assertThat(OperatorContext.required()).isEqualTo(alice);
        });

        assertThat(OperatorContext.current()).isEmpty();
    }

    @Test
    @DisplayName("操作抛异常时同样恢复")
    void restoresOnFailure() {
        assertThatThrownBy(() -> OperatorContext.callAs(alice, () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        assertThat(OperatorContext.current()).isEmpty();
    }

    @Test
    @DisplayName("没有操作者时 required 报错")
    void requiredFailsWithoutOperator() {
        assertThatThrownBy(OperatorContext::required)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No operator in OperatorContext");
    }

    @Test
    @DisplayName("不会自动带到别的线程，wrap 之后才会；任务结束后线程上不留下操作者")
    void wrapCarriesTheOperatorToAnotherThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            OperatorContext.set(alice);
            Runnable plain = () -> assertThat(OperatorContext.current()).isEmpty();
            Runnable wrapped = OperatorContext.wrap(() -> assertThat(OperatorContext.required()).isEqualTo(alice));

            CompletableFuture.runAsync(plain, executor).get();
            CompletableFuture.runAsync(wrapped, executor).get();
            CompletableFuture.runAsync(plain, executor).get();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("调用 wrap 时没有操作者则原样返回任务")
    void wrapWithoutOperatorReturnsTheTask() {
        Runnable task = () -> {
        };

        assertThat(OperatorContext.wrap(task)).isSameAs(task);
    }

    @Test
    @DisplayName("操作者标识不能为空白")
    void operatorIdMustNotBeBlank() {
        assertThatThrownBy(() -> Operator.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Operator.of("7").tenantId()).isNull();
    }
}
