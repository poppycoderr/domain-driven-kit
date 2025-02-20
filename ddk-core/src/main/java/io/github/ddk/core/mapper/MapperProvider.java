package io.github.ddk.core.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: 待优化设计
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
@Slf4j
@Component
public class MapperProvider {

    private final Map<String, ObjectMapper<?, ?>> mappers = new ConcurrentHashMap<>();
    private final Map<String, ObjectMapper<?, ?>> enhancedMappers = new ConcurrentHashMap<>();
    private final DefaultMapper<?, ?> defaultMapper;

    public MapperProvider(DefaultMapper<?, ?> defaultMapper, ApplicationContext context) {
        this.defaultMapper = defaultMapper;
        loadEnhancedMapper(context);
    }

    @SuppressWarnings("unchecked")
    public <S, T> ObjectMapper<S, T> lookup(Class<S> left, Class<T> right) {
        String keyPair = left.getSimpleName()+ right.getSimpleName();
        return (ObjectMapper<S, T>) mappers.computeIfAbsent(keyPair, k -> {
            // 自定义转换器
            ObjectMapper<S, T> customMapper = (ObjectMapper<S, T>) enhancedMappers.get(keyPair);
            if (customMapper != null) {
                return customMapper;
            }
            return defaultMapper;
        });
    }

    @SuppressWarnings("unchecked")
    private void loadEnhancedMapper(ApplicationContext context) {
        Map<String, Object> customMappers = context.getBeansWithAnnotation(EnhancedMapper.class);
        for (Object mapper : customMappers.values()) {
            Class<?> mapperClass = mapper.getClass();
            EnhancedMapper annotation = mapperClass.getAnnotation(EnhancedMapper.class);
            String key = annotation.source().getSimpleName()+ annotation.target().getSimpleName();
            enhancedMappers.put(key, (ObjectMapper<?, ?>) mapper);
            log.info("Registered custom mapper: {}", mapperClass.getSimpleName());
        }
    }
}

