package com.ddk.tracer.starter;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本 starter 注册的 {@code ContextPropagatingTaskDecorator} 被 Spring Boot 用在它创建的任务线程池上；ddk-core 为
 * {@code OperatorContext} 注册了上下文访问器，所以 {@code @Async} 方法里能拿到调用方的操作者（多租户应用里连同租户）。
 */
@DisplayName("@Async 方法里的操作者")
class AsyncOperatorPropagationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(TracingAutoConfigurations.withDdk())
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncConfiguration.class);

    @Test
    @DisplayName("@Async 方法看到调用方的操作者；没有操作者的调用看到的也是空")
    void asyncMethodsSeeTheCallersOperator() {
        runner.run(context -> {
            Background background = context.getBean(Background.class);
            Operator alice = Operator.of("alice", "tenant-1");

            Optional<Operator> inside = OperatorContext.callAs(alice, background::currentOperator).get(5, TimeUnit.SECONDS);
            Optional<Operator> anonymous = background.currentOperator().get(5, TimeUnit.SECONDS);

            assertThat(inside).contains(alice);
            assertThat(anonymous).isEmpty();
        });
    }

    @Test
    @DisplayName("开启虚拟线程后 @Async 方法跑在虚拟线程上，操作者和链路照样跟过去，调用方线程上的上下文不受影响")
    void virtualThreadsCarryTheOperatorAndTheTrace() {
        runner.withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class))
                .withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            Background background = context.getBean(Background.class);
            Tracer tracer = context.getBean(Tracer.class);
            Operator alice = Operator.of("alice", "tenant-1");
            String[] callerTrace = new String[1];

            // 和真实的请求一样，链路由一次观测开启（Web 请求的观测由 Spring 创建）
            Seen seen = Observation.createNotStarted("http.server.requests", context.getBean(ObservationRegistry.class)).observeChecked(() -> {
                callerTrace[0] = Objects.requireNonNull(tracer.currentSpan()).context().traceId();
                return OperatorContext.callAs(alice, background::seen).get(5, TimeUnit.SECONDS);
            });

            assertThat(seen.virtual()).isTrue();
            assertThat(seen.operator()).contains(alice);
            assertThat(seen.traceId()).isEqualTo(callerTrace[0]);
            assertThat(OperatorContext.current()).isEmpty();
            assertThat(tracer.currentSpan()).isNull();
        });
    }

    record Seen(
            boolean virtual,
            Optional<Operator> operator,
            @Nullable String traceId
    ) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncConfiguration {

        @Bean
        Background background(ObjectProvider<Tracer> tracer) {
            return new Background(tracer);
        }
    }

    static class Background {

        private final ObjectProvider<Tracer> tracer;

        Background(ObjectProvider<Tracer> tracer) {
            this.tracer = tracer;
        }

        @Async
        public CompletableFuture<Seen> seen() {
            Span current = tracer.getObject().currentSpan();
            return CompletableFuture.completedFuture(
                    new Seen(Thread.currentThread().isVirtual(), OperatorContext.current(), current == null ? null : current.context().traceId()));
        }

        @Async
        public CompletableFuture<Optional<Operator>> currentOperator() {
            return CompletableFuture.completedFuture(OperatorContext.current());
        }
    }
}
