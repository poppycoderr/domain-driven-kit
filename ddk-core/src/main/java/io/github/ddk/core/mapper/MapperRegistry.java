package io.github.ddk.core.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Elijah Du
 * @date 2025/2/11
 */
@Slf4j
@Component
public class MapperRegistry<S, T> {

    private final Map<MappingKey, ObjectMapper<S, T>> mapperCache = new ConcurrentHashMap<>();
    private final Map<MappingKey, ObjectMapper<S, T>> customMapperCache = new ConcurrentHashMap<>();
    private final DefaultMapper<S, T> defaultMapper;

    public MapperRegistry(DefaultMapper<S, T> defaultMapper, ApplicationContext context) {
        this.defaultMapper = defaultMapper;
        loadCustomMapper(context);
    }

    public ObjectMapper<S, T> lookupMapper(Class<S> sourceClass, Class<T> targetClass) {
        MappingKey key = new MappingKey(sourceClass, targetClass);
        return mapperCache.computeIfAbsent(key, k -> {
            // 自定义转换器
            ObjectMapper<S, T> customMapper = customMapperCache.get(key);
            if (customMapper != null) {
                return customMapper;
            }
            return defaultMapper;
        });
    }

    @SuppressWarnings("unchecked")
    private void loadCustomMapper(ApplicationContext context) {
        Map<String, Object> customMappers = context.getBeansWithAnnotation(CustomMapper.class);
        for (Object mapper : customMappers.values()) {
            Class<?> mapperClass = mapper.getClass();
            CustomMapper annotation = mapperClass.getAnnotation(CustomMapper.class);
            MappingKey key = new MappingKey(annotation.source(), annotation.target());
            customMapperCache.put(key, (ObjectMapper<S, T>) mapper);
            log.info("Registered custom mapper: {}", mapperClass.getSimpleName());
        }
    }
}

