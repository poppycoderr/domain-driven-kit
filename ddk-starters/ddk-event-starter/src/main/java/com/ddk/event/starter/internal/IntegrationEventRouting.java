package com.ddk.event.starter.internal;

import com.ddk.core.domain.Identifier;
import com.ddk.core.domain.IntegrationEvent;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 {@link IntegrationEvent} 为事件计算 Spring Modulith 的投递目标。
 * <p>
 * key 在这里直接取值后作为字面量交给 Modulith，而不是拼成 SpEL 表达式：属性名来自注解，拼接表达式等于在运行时执行一段字符串，
 * 取值出错时也更难定位。类型化标识取原始值，与它在消息体里的 JSON 形式一致。
 */
public final class IntegrationEventRouting {

    private final Map<Class<?>, Method> keyAccessors = new ConcurrentHashMap<>();

    public RoutingTarget route(Object event, IntegrationEvent annotation) {
        RoutingTarget.RoutingTargetBuilder target = RoutingTarget.forTarget(annotation.value());
        if (annotation.key().isEmpty()) {
            return target.withoutKey();
        }
        Object key = ReflectionUtils.invokeMethod(keyAccessor(event.getClass(), annotation.key()), event);
        if (key == null) {
            return target.withoutKey();
        }
        return target.andKey(String.valueOf(key instanceof Identifier<?> identifier ? identifier.value() : key));
    }

    private Method keyAccessor(Class<?> type, String property) {
        return keyAccessors.computeIfAbsent(type, t -> {
            Method method = ReflectionUtils.findMethod(t, property);
            if (method == null || method.getReturnType() == void.class) {
                throw new IllegalStateException("@IntegrationEvent(key = \"" + property + "\") on " + t.getName()
                        + " does not name a no-argument accessor");
            }
            ReflectionUtils.makeAccessible(method);
            return method;
        });
    }
}
