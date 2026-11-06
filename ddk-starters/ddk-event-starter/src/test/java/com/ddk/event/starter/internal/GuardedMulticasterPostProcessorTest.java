package com.ddk.event.starter.internal;

import com.ddk.event.starter.config.DdkEventProperties.OutsideTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("事务外发布检查的广播器代理")
class GuardedMulticasterPostProcessorTest {

    private static final String MULTICASTER = AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME;

    private final GuardedMulticasterPostProcessor processor = new GuardedMulticasterPostProcessor(() -> OutsideTransaction.FAIL);

    @Test
    @DisplayName("AOT 问类型时就给出代理类，和运行时代理用的是同一个类：原生镜像不能在运行时生成 CGLIB 类")
    void reportsTheProxyClassAheadOfTime() {
        Class<?> ahead = processor.determineBeanType(SimpleApplicationEventMulticaster.class, MULTICASTER);
        Object proxied = processor.postProcessAfterInitialization(new SimpleApplicationEventMulticaster(), MULTICASTER);

        assertThat(ahead).isNotEqualTo(SimpleApplicationEventMulticaster.class);
        assertThat(SimpleApplicationEventMulticaster.class).isAssignableFrom(ahead);
        assertThat(proxied.getClass()).isEqualTo(ahead);
    }

    @Test
    @DisplayName("别的 bean 不代理，类型也不变")
    void leavesOtherBeansAlone() {
        SimpleApplicationEventMulticaster other = new SimpleApplicationEventMulticaster();

        assertThat(processor.determineBeanType(SimpleApplicationEventMulticaster.class, "otherMulticaster")).isEqualTo(SimpleApplicationEventMulticaster.class);
        assertThat(processor.determineBeanType(String.class, MULTICASTER)).isEqualTo(String.class);
        assertThat(processor.postProcessAfterInitialization(other, "otherMulticaster")).isSameAs(other);
    }
}
