package com.ddk.event.starter.internal;

import com.ddk.core.domain.Identifier;
import com.ddk.core.domain.IntegrationEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 {@link IntegrationEvent} 为事件计算 Spring Modulith 的投递目标。
 * <p>
 * key 在这里直接取值后作为字面量交给 Modulith，而不是拼成 SpEL 表达式：属性名来自注解，拼接表达式等于在运行时执行一段字符串，
 * 取值出错时也更难定位。类型化标识取原始值，与它在消息体里的 JSON 形式一致。
 */
public final class IntegrationEventRouting {

    private final Map<String, Method> accessors = new ConcurrentHashMap<>();

    public static final String EVENT_ID_HEADER = "ddk-event-id";

    public static final String EVENT_TYPE_HEADER = "ddk-event-type";

    public static final String EVENT_VERSION_HEADER = "ddk-event-version";

    /**
     * 契约相关的消息头：{@value #EVENT_TYPE_HEADER}、{@value #EVENT_VERSION_HEADER}，事件声明了 {@link IntegrationEvent#id()} 时
     * 还有 {@value #EVENT_ID_HEADER}。
     */
    public Map<String, Object> headers(Object event) {
        IntegrationEvent annotation = event.getClass().getAnnotation(IntegrationEvent.class);
        if (annotation == null) {
            return Map.of();
        }
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(EVENT_TYPE_HEADER, typeOf(event.getClass(), annotation));
        headers.put(EVENT_VERSION_HEADER, String.valueOf(annotation.version()));
        if (!annotation.id().isEmpty()) {
            Object id = read(event, annotation.id(), "id");
            if (id != null) {
                headers.put(EVENT_ID_HEADER, String.valueOf(raw(id)));
            }
        }
        return headers;
    }

    public static String typeOf(Class<?> eventType, IntegrationEvent annotation) {
        return annotation.type().isBlank() ? eventType.getSimpleName() : annotation.type();
    }

    /**
     * 启动期校验一个事件类型的声明：key 与 id 指向的访问方法必须存在，版本号从 1 开始。
     *
     * @throws IllegalStateException 声明有误
     */
    public void validate(Class<?> eventType, IntegrationEvent annotation) {
        if (annotation.value().isBlank()) {
            throw new IllegalStateException("@IntegrationEvent on " + eventType.getName() + " has a blank target");
        }
        if (annotation.version() < 1) {
            throw new IllegalStateException("@IntegrationEvent on " + eventType.getName() + " must have version >= 1");
        }
        if (!annotation.key().isEmpty()) {
            accessor(eventType, annotation.key(), "key");
        }
        if (!annotation.id().isEmpty()) {
            accessor(eventType, annotation.id(), "id");
        }
    }

    public RoutingTarget route(Object event, IntegrationEvent annotation) {
        RoutingTarget.RoutingTargetBuilder target = RoutingTarget.forTarget(annotation.value());
        if (annotation.key().isEmpty()) {
            return target.withoutKey();
        }
        Object key = read(event, annotation.key(), "key");
        return key == null ? target.withoutKey() : target.andKey(String.valueOf(raw(key)));
    }

    private @Nullable Object read(Object event, String property, String attribute) {
        return ReflectionUtils.invokeMethod(accessor(event.getClass(), property, attribute), event);
    }

    private static Object raw(Object value) {
        return value instanceof Identifier<?> identifier ? identifier.value() : value;
    }

    private Method accessor(Class<?> type, String property, String attribute) {
        return accessors.computeIfAbsent(type.getName() + "#" + property, k -> {
            Method method = ReflectionUtils.findMethod(type, property);
            if (method == null || method.getReturnType() == void.class) {
                throw new IllegalStateException("@IntegrationEvent(" + attribute + " = \"" + property + "\") on " + type.getName()
                        + " does not name a no-argument accessor");
            }
            ReflectionUtils.makeAccessible(method);
            return method;
        });
    }
}
