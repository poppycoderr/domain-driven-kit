package com.ddk.concurrency.starter.internal;

import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.Idempotent;
import com.ddk.concurrency.starter.RateLimit;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 原生镜像里，{@link AggregateLock}、{@link Idempotent}、{@link RateLimit} 的 key 表达式（例如 {@code #command.requestId()}）
 * 要靠反射读参数。构建时找出声明了这些注解的方法，为它们的参数类型登记反射，应用不用自己登记。
 * <p>
 * 登记方式和 Jackson 绑定相同：record 组件、getter，以及它们返回的类型。
 */
public class KeyExpressionAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean bean) {
        Set<Class<?>> types = parameterTypes(bean.getBeanClass());
        if (types.isEmpty()) {
            return null;
        }
        return (context, code) -> registerHints(context.getRuntimeHints(), types);
    }

    static Set<Class<?>> parameterTypes(Class<?> beanClass) {
        Set<Class<?>> types = new LinkedHashSet<>();
        ReflectionUtils.doWithMethods(ClassUtils.getUserClass(beanClass), method -> {
            for (Class<?> type : method.getParameterTypes()) {
                if (!type.isPrimitive()) {
                    types.add(type);
                }
            }
        }, KeyExpressionAotProcessor::declaresKey);
        return types;
    }

    static void registerHints(RuntimeHints hints, Collection<Class<?>> types) {
        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), types.toArray(Class<?>[]::new));
    }

    private static boolean declaresKey(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, AggregateLock.class)
                || AnnotatedElementUtils.hasAnnotation(method, Idempotent.class)
                || AnnotatedElementUtils.hasAnnotation(method, RateLimit.class);
    }
}
