package com.ddk.concurrency.starter.internal;

import com.ddk.concurrency.starter.Idempotent;
import com.ddk.concurrency.starter.RateLimit;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
    @DisplayName("参数简单类名相同的重载各自一个默认作用域；数组和基本类型也写全")
    void overloadsWhoseParametersShareASimpleNameHaveSeparateScopes() throws Exception {
        String utilDate = ConcurrencyInterceptor.defaultScope(Overloads.class.getMethod("submit", java.util.Date.class));
        String sqlDate = ConcurrencyInterceptor.defaultScope(Overloads.class.getMethod("submit", java.sql.Date.class));

        assertThat(utilDate).isNotEqualTo(sqlDate);
        assertThat(sqlDate).endsWith("$Overloads#submit(java.sql.Date)");
        assertThat(ConcurrencyInterceptor.defaultScope(Overloads.class.getMethod("submit", String[].class, int.class)))
                .endsWith("$Overloads#submit(java.lang.String[],int)");
    }

    static class Overloads {

        public void submit(java.util.Date command) {
        }

        public void submit(java.sql.Date command) {
        }

        public void submit(String[] ids, int priority) {
        }
    }

    @Test
    @DisplayName("默认作用域是完整类名、方法名和参数类型；代理不改变它")
    void theDefaultScopeNamesTheDeclaringMethod() throws Exception {
        assertThat(ConcurrencyInterceptor.defaultScope(Checkout.class.getMethod("pay", String.class)))
                .isEqualTo("com.ddk.concurrency.starter.internal.ConcurrencyInterceptorTest$Checkout#pay(java.lang.String)");

        RequestRegistry registry = mock(RequestRegistry.class);
        when(registry.register(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new RequestRegistry.Registration("name", "token"));
        assertThatThrownBy(() -> proxy(registry).pay("req-2")).isInstanceOf(IllegalStateException.class);
        verify(registry).register(eq("com.ddk.concurrency.starter.internal.ConcurrencyInterceptorTest$Checkout#pay(java.lang.String)"), eq("req-2"),
                any(Duration.class));
    }

    public static class Signup {

        @Idempotent(key = "#requestId")
        @RateLimit(limit = 5, period = "1m")
        public String register(String requestId) {
            return "registered " + requestId;
        }
    }

    @Test
    @DisplayName("按租户区分时，防重复和限流的 key 带上租户；没有租户的调用共用 -: 这一份")
    void tenantScopedKeysCarryTheTenant() {
        RequestRegistry registry = mock(RequestRegistry.class);
        RateLimiters limiters = mock(RateLimiters.class);
        when(registry.register(anyString(), anyString(), any(Duration.class))).thenReturn(new RequestRegistry.Registration("name", "token"));
        when(limiters.tryAcquire(anyString(), anyString(), anyLong(), any(Duration.class))).thenReturn(true);
        Signup signup = proxy(new Signup(), registry, limiters, true);

        OperatorContext.runAs(Operator.of("1", "t1"), () -> signup.register("req-1"));
        signup.register("req-2");
        OperatorContext.runAs(Operator.of("1", " "), () -> signup.register("req-3"));

        verify(registry).register(anyString(), eq("@t1:req-1"), any(Duration.class));
        verify(registry).register(anyString(), eq("-:req-2"), any(Duration.class));
        verify(registry).register(anyString(), eq("-:req-3"), any(Duration.class));
        verify(limiters).tryAcquire(anyString(), eq("@t1:*"), eq(5L), any(Duration.class));
        verify(limiters, times(2)).tryAcquire(anyString(), eq("-:*"), eq(5L), any(Duration.class));
        assertThatThrownBy(() -> OperatorContext.runAs(Operator.of("1", "a:b"), () -> signup.register("req-4")))
                .hasMessageContaining("colon");
    }

    @Test
    @DisplayName("默认不区分租户，key 与以前一样")
    void keysIgnoreTheTenantByDefault() {
        RequestRegistry registry = mock(RequestRegistry.class);
        RateLimiters limiters = mock(RateLimiters.class);
        when(registry.register(anyString(), anyString(), any(Duration.class))).thenReturn(new RequestRegistry.Registration("name", "token"));
        when(limiters.tryAcquire(anyString(), anyString(), anyLong(), any(Duration.class))).thenReturn(true);

        OperatorContext.runAs(Operator.of("1", "t1"), () -> proxy(new Signup(), registry, limiters, false).register("req-1"));

        verify(registry).register(anyString(), eq("req-1"), any(Duration.class));
        verify(limiters).tryAcquire(anyString(), eq("*"), eq(5L), any(Duration.class));
    }

    private static Checkout proxy(RequestRegistry registry) {
        return proxy(new Checkout(), registry, mock(RateLimiters.class), false);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(T target, RequestRegistry registry, RateLimiters limiters, boolean tenantScoped) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("registry", registry);
        beans.addBean("limiters", limiters);
        ConcurrencyInterceptor interceptor = new ConcurrencyInterceptor(beans.getBeanProvider(com.ddk.concurrency.starter.AggregateLocks.class),
                beans.getBeanProvider(RequestRegistry.class), beans.getBeanProvider(RateLimiters.class), Duration.ofMinutes(10),
                Duration.ofSeconds(3), null, tenantScoped);
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }
}
