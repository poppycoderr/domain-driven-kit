package io.github.ddk.core.mapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;

import java.util.List;
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
public class MapperProvider<L, R> {

    private final Map<Pair<?, ?>, ObjectMapper<L, R>> mapperCache = new ConcurrentHashMap<>();
    private final Map<Pair<?, ?>, ObjectMapper<L, R>> customMapperCache = new ConcurrentHashMap<>();
    private final DefaultMapper<L, R> defaultMapper;

    public MapperProvider(DefaultMapper<L, R> defaultMapper, ApplicationContext context) {
        this.defaultMapper = defaultMapper;
        loadCustomMapper(context);
    }

    public L mapToLeft(R source) {
        return lookup().mapToLeft(source);
    }

    public List<L> mapToLeft(List<R> sources) {
        return lookup().mapToLeft(sources);
    }

    public R mapToRight(L source) {
        return lookup().mapToRight(source);
    }

    public List<R> mapToRight(List<L> sources) {
        return lookup().mapToRight(sources);
    }

    private ObjectMapper<L, R> lookup() {
        Class<?>[] types = GenericTypeResolver.resolveTypeArguments(this.getClass(), MapperProvider.class);
        assert types != null;
        Pair<?, ?> keyPair = Pair.of(types[0], types[1]);
        return mapperCache.computeIfAbsent(keyPair, k -> {
            // 自定义转换器
            ObjectMapper<L, R> customMapper = customMapperCache.get(keyPair);
            if (customMapper != null) {
                return customMapper;
            }
            return defaultMapper;
        });
    }

    @SuppressWarnings("unchecked")
    private void loadCustomMapper(ApplicationContext context) {
        Map<String, Object> customMappers = context.getBeansWithAnnotation(EnhancedMapper.class);
        for (Object mapper : customMappers.values()) {
            Class<?> mapperClass = mapper.getClass();
            EnhancedMapper annotation = mapperClass.getAnnotation(EnhancedMapper.class);
            Pair<?, ?> key = Pair.of(annotation.left(), annotation.right());
            customMapperCache.put(key, (ObjectMapper<L, R>) mapper);
            log.info("Registered custom mapper: {}", mapperClass.getSimpleName());
        }
    }
}

