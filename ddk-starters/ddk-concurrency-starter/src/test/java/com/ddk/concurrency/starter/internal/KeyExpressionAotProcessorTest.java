package com.ddk.concurrency.starter.internal;

import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.Idempotent;
import com.ddk.concurrency.starter.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("原生镜像：key 表达式读取的参数类型")
class KeyExpressionAotProcessorTest {

    @Test
    @DisplayName("收集标了锁、防重复提交、限流的方法的参数类型，其余方法的不收")
    void collectsTheParametersOfAnnotatedMethods() {
        assertThat(KeyExpressionAotProcessor.parameterTypes(Inventory.class)).containsExactlyInAnyOrder(Reserve.class, Check.class, String.class);
    }

    @Test
    @DisplayName("登记的反射够 SpEL 调用 record 的组件方法")
    void registersWhatTheKeyExpressionsCall() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        KeyExpressionAotProcessor.registerHints(hints, KeyExpressionAotProcessor.parameterTypes(Inventory.class));

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Reserve.class.getMethod("requestId"))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Reserve.class.getMethod("skuId"))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Check.class.getMethod("tenant"))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(Unrelated.class)).rejects(hints);
    }

    @Test
    @DisplayName("没有这些注解的 Bean 不产生任何登记")
    void ignoresOtherBeans() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("inventory", new RootBeanDefinition(Inventory.class));
        beans.registerBeanDefinition("other", new RootBeanDefinition(Other.class));
        KeyExpressionAotProcessor processor = new KeyExpressionAotProcessor();

        assertThat(processor.processAheadOfTime(RegisteredBean.of(beans, "inventory"))).isNotNull();
        assertThat(processor.processAheadOfTime(RegisteredBean.of(beans, "other"))).isNull();
    }

    record Reserve(String requestId, Long skuId) {
    }

    record Check(String tenant) {
    }

    record Unrelated(String value) {
    }

    static class Inventory {

        @AggregateLock(type = "sku", id = "#command.skuId()")
        @Idempotent(key = "#command.requestId()")
        public void reserve(Reserve command) {
        }

        @RateLimit(limit = 1, period = "1m", key = "#check.tenant()")
        public void check(Check check, int attempt) {
        }

        @Idempotent(key = "#name")
        public void rename(String name) {
        }

        public void unrelated(Unrelated unrelated) {
        }
    }

    static class Other {

        public void unrelated(Unrelated unrelated) {
        }
    }
}
