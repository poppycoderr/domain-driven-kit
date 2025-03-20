package com.ddk.core.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 映射器提供者
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
@Slf4j
@Component
public class MapperProvider {

    private final Map<String, ObjectMapper<?, ?>> mappers = new ConcurrentHashMap<>();
    private final Map<String, ObjectMapper<?, ?>> enhancedMappers = new ConcurrentHashMap<>();
    private final DefaultMapper defaultMapper;

    public MapperProvider(DefaultMapper defaultMapper, ApplicationContext context) {
        this.defaultMapper = defaultMapper;
        loadEnhancedMapper(context);
    }

    /**
     * 查找映射器
     * <p>
     * TODO: 待优化，target 类型自动推断
     *
     * @param source 源对象类型
     * @param target 目标对象类型
     * @return 映射器
     */
    @SuppressWarnings("unchecked")
    public <S, T> ObjectMapper<S, T> lookup(Class<S> source, Class<T> target) {
        String keyPair = source.getSimpleName() + target.getSimpleName();
        return (ObjectMapper<S, T>) mappers.computeIfAbsent(keyPair, k -> {
            ObjectMapper<S, T> customMapper = (ObjectMapper<S, T>) enhancedMappers.get(keyPair);
            if (customMapper != null) {
                return customMapper;
            }
            return defaultMapper;
        });
    }

    /**
     * 加载增强映射器
     *
     * @param context Spring 应用上下文
     */
    @SuppressWarnings("unchecked")
    private void loadEnhancedMapper(ApplicationContext context) {
        Map<String, Object> customMappers = context.getBeansWithAnnotation(EnhancedMapper.class);
        for (Object mapper : customMappers.values()) {
            Class<?> mapperClass = mapper.getClass();
            EnhancedMapper annotation = mapperClass.getAnnotation(EnhancedMapper.class);
            String key = annotation.source().getSimpleName() + annotation.target().getSimpleName();
            enhancedMappers.put(key, (ObjectMapper<?, ?>) mapper);
            log.info("Registered custom mapper: {}", mapperClass.getSimpleName());
        }
    }
}

