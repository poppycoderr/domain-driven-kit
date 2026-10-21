package com.ddk.tracer.starter;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

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

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncConfiguration {

        @Bean
        Background background() {
            return new Background();
        }
    }

    static class Background {

        @Async
        public CompletableFuture<Optional<Operator>> currentOperator() {
            return CompletableFuture.completedFuture(OperatorContext.current());
        }
    }
}
