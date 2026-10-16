package com.ddk.concurrency.starter.internal;

import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.AggregateLocks;
import com.ddk.concurrency.starter.Idempotent;
import com.ddk.concurrency.starter.RateLimit;
import com.ddk.core.exception.DuplicateRequestException;
import com.ddk.core.exception.RateLimitedException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 处理 {@link RateLimit}、{@link Idempotent} 与 {@link AggregateLock}：先限流，再登记请求，再加锁，最后才进入事务。
 * <p>
 * 重复的请求在加锁之前就被拒绝，不占用锁的等待时间。依赖按需获取：拦截器随 Bean 后置处理器很早就被创建，那时不应连带初始化 Redis 客户端。
 * 没有可用的 {@code RedissonClient} 时调用直接失败，而不是悄悄地不加锁。
 */
public class ConcurrencyInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyInterceptor.class);

    private final ObjectProvider<AggregateLocks> locks;

    private final ObjectProvider<RequestRegistry> requests;

    private final ObjectProvider<RateLimiters> rates;

    private final Duration defaultTtl;

    private final Duration defaultWaitTime;

    private final @Nullable Duration defaultLeaseTime;

    private final KeyExpressions expressions = new KeyExpressions();

    public ConcurrencyInterceptor(ObjectProvider<AggregateLocks> locks, ObjectProvider<RequestRegistry> requests,
            ObjectProvider<RateLimiters> rates, Duration defaultTtl, Duration defaultWaitTime, @Nullable Duration defaultLeaseTime) {
        this.locks = locks;
        this.requests = requests;
        this.rates = rates;
        this.defaultTtl = defaultTtl;
        this.defaultWaitTime = defaultWaitTime;
        this.defaultLeaseTime = defaultLeaseTime;
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        if (target == null) {
            return invocation.proceed();
        }
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), target.getClass());
        checkRate(invocation, method, target);
        Idempotent idempotent = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        if (idempotent == null) {
            return locked(invocation, method, target);
        }

        RequestRegistry registry = requests.getIfAvailable(() -> {
            throw missingClient("@Idempotent", method);
        });
        String scope = idempotent.scope().isEmpty() ? defaultScope(method) : idempotent.scope();
        String key = expressions.evaluate(idempotent.key(), method, target, invocation);
        Duration ttl = idempotent.ttl().isEmpty() ? defaultTtl : DurationStyle.detectAndParse(idempotent.ttl());
        RequestRegistry.Registration registration = registry.register(scope, key, ttl);
        if (registration == null) {
            throw new DuplicateRequestException();
        }
        try {
            return locked(invocation, method, target);
        } catch (Throwable failure) {
            release(registry, registration, failure);
            throw failure;
        }
    }

    /**
     * 撤销失败不能替换掉业务异常：调用方要看到的是方法为什么失败。撤销的异常附在业务异常上并记日志，登记随 TTL 过期。
     */
    private static void release(RequestRegistry registry, RequestRegistry.Registration registration, Throwable failure) {
        try {
            registry.release(registration);
        } catch (RuntimeException releaseFailure) {
            log.warn("Could not release request registration {}; it expires with its TTL", registration.name(), releaseFailure);
            failure.addSuppressed(releaseFailure);
        }
    }

    private void checkRate(MethodInvocation invocation, Method method, Object target) {
        RateLimit rateLimit = AnnotatedElementUtils.findMergedAnnotation(method, RateLimit.class);
        if (rateLimit == null) {
            return;
        }
        RateLimiters limiters = rates.getIfAvailable(() -> {
            throw missingClient("@RateLimit", method);
        });
        String scope = rateLimit.scope().isEmpty() ? defaultScope(method) : rateLimit.scope();
        String key = rateLimit.key().isEmpty() ? "*" : expressions.evaluate(rateLimit.key(), method, target, invocation);
        if (!limiters.tryAcquire(scope, key, rateLimit.limit(), DurationStyle.detectAndParse(rateLimit.period()))) {
            throw new RateLimitedException();
        }
    }

    /**
     * 默认作用域：声明方法的类的完整类名、方法名和参数类型，例如 {@code com.acme.OrderService#place(PlaceOrderCommand)}。
     * <p>
     * 用完整类名，不同包里同名的类不会共用一个作用域；带上参数类型，重载的方法也互不影响。{@code method} 是目标类上的方法，
     * 不是代理类上的，所以有没有代理、是 JDK 代理还是 CGLIB 代理，作用域都一样。
     */
    static String defaultScope(Method method) {
        StringJoiner parameters = new StringJoiner(",", "(", ")");
        for (Class<?> type : method.getParameterTypes()) {
            parameters.add(type.getSimpleName());
        }
        return method.getDeclaringClass().getName() + "#" + method.getName() + parameters;
    }

    private @Nullable Object locked(MethodInvocation invocation, Method method, Object target) throws Throwable {
        AggregateLock lock = AnnotatedElementUtils.findMergedAnnotation(method, AggregateLock.class);
        if (lock == null) {
            return invocation.proceed();
        }
        AggregateLocks aggregateLocks = locks.getIfAvailable(() -> {
            throw missingClient("@AggregateLock", method);
        });
        String id = expressions.evaluate(lock.id(), method, target, invocation);
        Duration waitTime = lock.waitTime().isEmpty() ? defaultWaitTime : DurationStyle.detectAndParse(lock.waitTime());
        Duration leaseTime = lock.leaseTime().isEmpty() ? defaultLeaseTime : DurationStyle.detectAndParse(lock.leaseTime());
        // 被拦截的方法可以返回 null，而 Action 的结果不可为空，所以结果另外带出来
        AtomicReference<@Nullable Object> result = new AtomicReference<>();
        aggregateLocks.execute(lock.type(), id, waitTime, leaseTime, () -> {
            result.set(invocation.proceed());
            return Boolean.TRUE;
        });
        return result.get();
    }

    private static IllegalStateException missingClient(String annotation, Method method) {
        return new IllegalStateException(annotation + " on " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                + " needs a RedissonClient: configure spring.data.redis.* or declare a RedissonClient bean");
    }
}
