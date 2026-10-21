package com.ddk.core.context.internal;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import io.micrometer.context.ThreadLocalAccessor;
import org.jspecify.annotations.Nullable;

/**
 * 让 Micrometer 的上下文传播认识 {@link OperatorContext}：Spring 的 {@code ContextPropagatingTaskDecorator}、
 * Reactor 的自动传播等在切换线程时，把当前的操作者一起带过去，任务结束后恢复目标线程原来的值。
 * <p>
 * 通过 {@code META-INF/services} 注册，类路径上有 {@code io.micrometer:context-propagation} 时自动生效，不需要配置。
 */
public class OperatorThreadLocalAccessor implements ThreadLocalAccessor<Operator> {

    public static final String KEY = "ddk.operator";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public @Nullable Operator getValue() {
        return OperatorContext.current().orElse(null);
    }

    @Override
    public void setValue(Operator value) {
        OperatorContext.set(value);
    }

    @Override
    public void setValue() {
        OperatorContext.clear();
    }
}
