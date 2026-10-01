package com.ddk.concurrency.starter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交：同一个 key 在有效期内只执行一次，再次提交时抛出 {@code DuplicateRequestException}。
 * <p>
 * 方法抛出异常时登记会被撤销，调用方可以用同一个 key 重试。它只拒绝重复的请求，不会重放第一次的结果。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    /**
     * 取请求标识的 SpEL 表达式，例如 {@code "#command.requestId()"}。应当是客户端生成、重试时保持不变的值。
     */
    String key();

    /**
     * key 的作用域，不同用例之间互不影响。留空用「类名.方法名」。
     */
    String scope() default "";

    /**
     * 登记保留的时间，例如 {@code "10m"}。留空用 {@code ddk.concurrency.idempotent.ttl}。
     */
    String ttl() default "";
}
