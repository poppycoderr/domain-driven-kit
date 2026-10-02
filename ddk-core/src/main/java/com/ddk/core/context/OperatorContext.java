package com.ddk.core.context;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 当前线程上的操作者。
 * <p>
 * 入口（Web 过滤器、消息监听、定时任务）在开始处理时设置，结束时清除；持久化层据此填写审计字段、追加租户条件。
 * 优先用 {@link #runAs(Operator, Runnable)} / {@link #callAs(Operator, Supplier)}：它们在结束时恢复之前的值，
 * 线程被线程池复用时不会把上一次的操作者带给下一次请求。
 * <p>
 * 值保存在线程上，不会自动带到别的线程。把任务交给线程池或 {@code @Async} 时，用 {@link #wrap(Runnable)} 把当前的操作者带过去。
 */
public final class OperatorContext {

    private static final ThreadLocal<@Nullable Operator> CURRENT = new ThreadLocal<>();

    private OperatorContext() {
    }

    public static Optional<Operator> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * @throws IllegalStateException 当前线程上没有操作者
     */
    public static Operator required() {
        Operator operator = CURRENT.get();
        if (operator == null) {
            throw new IllegalStateException("No operator in OperatorContext; set it at the entry point of the request");
        }
        return operator;
    }

    /**
     * 设置后必须在 finally 里调用 {@link #clear()}。能用 {@link #runAs(Operator, Runnable)} 时不要直接用这一对方法。
     */
    public static void set(Operator operator) {
        CURRENT.set(operator);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runAs(Operator operator, Runnable action) {
        callAs(operator, () -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    public static <T> T callAs(Operator operator, Supplier<T> action) {
        Operator previous = CURRENT.get();
        CURRENT.set(operator);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * 把调用时的操作者绑定到任务上；任务在别的线程执行时，以这个操作者的身份运行。调用时没有操作者则原样返回。
     */
    public static Runnable wrap(Runnable task) {
        Operator captured = CURRENT.get();
        return captured == null ? task : () -> runAs(captured, task);
    }
}
