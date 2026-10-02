package com.ddk.concurrency.starter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流：同一个 key 在一个时间窗口内最多执行 {@link #limit()} 次，超过时抛出 {@code RateLimitedException}。
 * <p>
 * key 通常是用户或租户的标识，于是每个用户、每个租户各有一份额度；不写 key 时整个用例共用一份。计数放在 Redis 里，所有实例共享。
 * 它在防重复提交与聚合锁之前生效：被限流的调用不占用请求登记，也不等待锁。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    /**
     * 一个时间窗口内允许的次数。
     */
    long limit();

    /**
     * 时间窗口，例如 {@code "1m"}、{@code "10s"}。
     */
    String period();

    /**
     * 取限流维度的 SpEL 表达式，例如 {@code "#command.userId()"}。留空表示整个用例共用一份额度。
     */
    String key() default "";

    /**
     * 额度的作用域，不同用例之间互不影响。留空用「类名.方法名」。
     */
    String scope() default "";
}
