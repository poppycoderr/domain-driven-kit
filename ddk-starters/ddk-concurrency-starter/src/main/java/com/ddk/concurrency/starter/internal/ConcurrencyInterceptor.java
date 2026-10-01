package com.ddk.concurrency.starter.internal;

import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.AggregateLocks;
import com.ddk.concurrency.starter.Idempotent;
import com.ddk.core.exception.DuplicateRequestException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 处理 {@link Idempotent} 与 {@link AggregateLock}：先登记请求，再加锁，最后才进入事务。
 * <p>
 * 重复的请求在加锁之前就被拒绝，不占用锁的等待时间。依赖按需获取：拦截器随 Bean 后置处理器很早就被创建，那时不应连带初始化 Redis 客户端。
 * 没有可用的 {@code RedissonClient} 时调用直接失败，而不是悄悄地不加锁。
 */
public class ConcurrencyInterceptor implements MethodInterceptor {

    private final ObjectProvider<AggregateLocks> locks;

    private final ObjectProvider<RequestRegistry> requests;

    private final Duration defaultTtl;

    private final Duration defaultWaitTime;

    private final @Nullable Duration defaultLeaseTime;

    private final KeyExpressions expressions = new KeyExpressions();

    public ConcurrencyInterceptor(ObjectProvider<AggregateLocks> locks, ObjectProvider<RequestRegistry> requests, Duration defaultTtl,
            Duration defaultWaitTime, @Nullable Duration defaultLeaseTime) {
        this.locks = locks;
        this.requests = requests;
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
        Idempotent idempotent = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        if (idempotent == null) {
            return locked(invocation, method, target);
        }

        RequestRegistry registry = requests.getIfAvailable(() -> {
            throw missingClient("@Idempotent", method);
        });
        String scope = idempotent.scope().isEmpty() ? method.getDeclaringClass().getSimpleName() + "." + method.getName() : idempotent.scope();
        String key = expressions.evaluate(idempotent.key(), method, target, invocation.getArguments());
        Duration ttl = idempotent.ttl().isEmpty() ? defaultTtl : DurationStyle.detectAndParse(idempotent.ttl());
        if (!registry.register(scope, key, ttl)) {
            throw new DuplicateRequestException();
        }
        try {
            return locked(invocation, method, target);
        } catch (Throwable failure) {
            registry.release(scope, key);
            throw failure;
        }
    }

    private @Nullable Object locked(MethodInvocation invocation, Method method, Object target) throws Throwable {
        AggregateLock lock = AnnotatedElementUtils.findMergedAnnotation(method, AggregateLock.class);
        if (lock == null) {
            return invocation.proceed();
        }
        AggregateLocks aggregateLocks = locks.getIfAvailable(() -> {
            throw missingClient("@AggregateLock", method);
        });
        String id = expressions.evaluate(lock.id(), method, target, invocation.getArguments());
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
