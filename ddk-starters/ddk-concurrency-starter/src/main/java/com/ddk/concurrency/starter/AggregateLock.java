package com.ddk.concurrency.starter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 执行方法期间独占一个聚合：同一时刻只有一个操作能处理同一个聚合实例。
 * <p>
 * 标在应用服务方法上。锁在事务之外获取、在事务结束之后释放，所以下一个操作加载到的一定是已提交的状态。
 * 等待时间内拿不到锁时抛出 {@code AggregateBusyException}。它与乐观锁互补而不是替代：乐观锁保证不会互相覆盖，
 * 聚合锁让冲突的操作排队，避免做完再被丢弃。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AggregateLock {

    /**
     * 聚合类型，例如 {@code "order"}，是锁名的一部分。
     */
    String type();

    /**
     * 取聚合标识的 SpEL 表达式，按参数名引用方法参数，例如 {@code "#command.orderId()"}。类型化标识取原始值。
     */
    String id();

    /**
     * 等待锁的最长时间，例如 {@code "3s"}、{@code "0"}（不等待）。留空用 {@code ddk.concurrency.lock.wait-time}。
     */
    String waitTime() default "";

    /**
     * 持有锁的最长时间，到期自动释放。留空用 {@code ddk.concurrency.lock.lease-time}；两者都没有设置时由 Redisson 的看门狗自动续期，
     * 直到方法结束。
     */
    String leaseTime() default "";
}
