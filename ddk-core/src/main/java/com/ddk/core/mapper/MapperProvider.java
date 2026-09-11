package com.ddk.core.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 映射器注册表。
 * <p>
 * 启动时扫描所有带 {@link EnhancedMapper} 的 Bean，按「源类型 -> 目标类型」建立索引，
 * 供仓储等基础设施在运行时查找。
 *
 * <h2>两条设计约束</h2>
 * <ol>
 *     <li><b>key 用全限定类名。</b>早期用 {@code getSimpleName()} 拼接，
 *     {@code com.a.User -> com.a.UserPO} 与 {@code com.b.User -> com.b.UserPO}
 *     会碰撞成同一个 key，后注册的静默覆盖前一个——在多限界上下文的项目里几乎必然发生。</li>
 *     <li><b>查不到就失败，不做兜底。</b>理由见 {@link MissingMapperException}。</li>
 * </ol>
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
@Slf4j
@Component
public class MapperProvider {

    /** key: 源类型全限定名 + "->" + 目标类型全限定名 */
    private final Map<String, ObjectMapper<?, ?>> mappers = new ConcurrentHashMap<>();

    public MapperProvider(ApplicationContext context) {
        loadEnhancedMappers(context);
    }

    /**
     * 查找映射器。
     *
     * @param source 源对象类型
     * @param target 目标对象类型
     * @return 映射器，一定非 null
     * @throws MissingMapperException 未注册对应映射器时抛出
     */
    @SuppressWarnings("unchecked")
    public <S, T> ObjectMapper<S, T> lookup(Class<S> source, Class<T> target) {
        ObjectMapper<?, ?> mapper = mappers.get(keyOf(source, target));
        if (mapper == null) {
            throw new MissingMapperException(source, target);
        }
        return (ObjectMapper<S, T>) mapper;
    }

    /**
     * 是否已注册对应映射器。供启动期自检使用，不改变查找语义。
     */
    public boolean contains(Class<?> source, Class<?> target) {
        return mappers.containsKey(keyOf(source, target));
    }

    /**
     * 已注册的映射方向数量。
     */
    public int size() {
        return mappers.size();
    }

    /**
     * 扫描并注册所有 {@link EnhancedMapper}。
     *
     * @throws IllegalStateException 映射器没有实现 {@link ObjectMapper}，
     *                               或同一映射方向被注册多次时抛出——重复注册意味着
     *                               运行时用哪一个取决于扫描顺序，必须在启动期暴露
     */
    private void loadEnhancedMappers(ApplicationContext context) {
        for (Map.Entry<String, Object> entry : context.getBeansWithAnnotation(EnhancedMapper.class).entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            if (!(bean instanceof ObjectMapper<?, ?> mapper)) {
                throw new IllegalStateException(
                        "@EnhancedMapper 标注的 " + bean.getClass().getName()
                                + " 没有实现 ObjectMapper，无法作为映射器注册");
            }

            // 用 findAnnotationOnBean 而不是 bean.getClass().getAnnotation()：
            // 被 AOP 代理包装后，代理类上拿不到原始注解
            EnhancedMapper annotation = context.findAnnotationOnBean(beanName, EnhancedMapper.class);
            if (annotation == null) {
                continue;
            }

            String key = keyOf(annotation.source(), annotation.target());
            ObjectMapper<?, ?> previous = mappers.putIfAbsent(key, mapper);
            if (previous != null && previous != mapper) {
                throw new IllegalStateException(
                        "映射方向 " + key + " 被重复注册："
                                + previous.getClass().getName() + " 与 " + mapper.getClass().getName());
            }
            log.debug("Registered mapper {} for {}", mapper.getClass().getSimpleName(), key);
        }
        log.info("MapperProvider initialized with {} mapping direction(s)", mappers.size());
    }

    private static String keyOf(Class<?> source, Class<?> target) {
        return source.getName() + "->" + target.getName();
    }
}
