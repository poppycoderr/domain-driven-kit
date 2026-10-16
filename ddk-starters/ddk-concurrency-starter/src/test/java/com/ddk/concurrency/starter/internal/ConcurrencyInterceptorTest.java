package com.ddk.concurrency.starter.internal;

import com.ddk.concurrency.starter.Idempotent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("请求登记的撤销")
class ConcurrencyInterceptorTest {

    public static class Checkout {

        @Idempotent(key = "#requestId")
        public String pay(String requestId) {
            throw new IllegalStateException("payment gateway down");
        }
    }

    @Test
    @DisplayName("撤销登记失败时，调用方看到的仍是业务异常，撤销的异常附在它上面")
    void aFailedReleaseDoesNotHideTheBusinessFailure() {
        RequestRegistry registry = mock(RequestRegistry.class);
        RequestRegistry.Registration registration = new RequestRegistry.Registration("ddk:idempotent:scope:req-1", "token");
        when(registry.register(anyString(), eq("req-1"), any(Duration.class))).thenReturn(registration);
        doThrow(new IllegalStateException("redis unreachable")).when(registry).release(registration);

        assertThatThrownBy(() -> proxy(registry).pay("req-1"))
                .hasMessage("payment gateway down")
                .satisfies(failure -> assertThat(failure.getSuppressed()).singleElement()
                        .satisfies(suppressed -> assertThat(suppressed).hasMessage("redis unreachable")));
        verify(registry).release(registration);
    }

    @Test
    @DisplayName("默认作用域是完整类名、方法名和参数类型；代理不改变它")
    void theDefaultScopeNamesTheDeclaringMethod() throws Exception {
        assertThat(ConcurrencyInterceptor.defaultScope(Checkout.class.getMethod("pay", String.class)))
                .isEqualTo("com.ddk.concurrency.starter.internal.ConcurrencyInterceptorTest$Checkout#pay(String)");

        RequestRegistry registry = mock(RequestRegistry.class);
        when(registry.register(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new RequestRegistry.Registration("name", "token"));
        assertThatThrownBy(() -> proxy(registry).pay("req-2")).isInstanceOf(IllegalStateException.class);
        verify(registry).register(eq("com.ddk.concurrency.starter.internal.ConcurrencyInterceptorTest$Checkout#pay(String)"), eq("req-2"),
                any(Duration.class));
    }

    private static Checkout proxy(RequestRegistry registry) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("registry", registry);
        ConcurrencyInterceptor interceptor = new ConcurrencyInterceptor(beans.getBeanProvider(com.ddk.concurrency.starter.AggregateLocks.class),
                beans.getBeanProvider(RequestRegistry.class), beans.getBeanProvider(RateLimiters.class), Duration.ofMinutes(10),
                Duration.ofSeconds(3), null);
        ProxyFactory factory = new ProxyFactory(new Checkout());
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (Checkout) factory.getProxy();
    }
}
