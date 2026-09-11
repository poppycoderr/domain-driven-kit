package com.ddk.core.mapper;

/**
 * 找不到对应的 {@link ObjectMapper} 时抛出。
 * <p>
 * 这个异常存在的理由，是替换掉一个更糟的行为：早期版本在查不到映射器时会退回
 * MapStruct 为 {@code ObjectMapper<Object, Object>} 生成的默认实现，而它生成的代码是
 *
 * <pre>{@code
 * public Object map(Object source) {
 *     if (source == null) return null;
 *     return new Object();      // 字段全部丢失
 * }
 * }</pre>
 * <p>
 * 于是仓储拿到一个空 {@code Object} 再 checkcast 成目标类型，在运行时抛
 * {@code ClassCastException}——报错位置离真正的原因（忘了注册 Mapper）很远。
 * 现在改为在查找时立刻失败，并在消息里直接给出补救写法。
 *
 * @author Elijah Du
 * @date 2026/9/11
 */
public class MissingMapperException extends IllegalStateException {

    private final transient Class<?> source;
    private final transient Class<?> target;

    public MissingMapperException(Class<?> source, Class<?> target) {
        super(buildMessage(source, target));
        this.source = source;
        this.target = target;
    }

    private static String buildMessage(Class<?> source, Class<?> target) {
        return """
                找不到 %s -> %s 的映射器。

                请显式提供一个 MapStruct 映射器并用 @EnhancedMapper 标注：

                    @Mapper(config = MapperConfiguration.class)
                    @EnhancedMapper(source = %s.class, target = %s.class)
                    public interface %sTo%sMapper extends ObjectMapper<%s, %s> {
                    }

                注意映射是有方向的：Entity -> PO 和 PO -> Entity 是两个映射器，需要各注册一次。"""
                .formatted(source.getName(), target.getName(),
                        source.getSimpleName(), target.getSimpleName(),
                        source.getSimpleName(), target.getSimpleName(),
                        source.getSimpleName(), target.getSimpleName());
    }

    public Class<?> getSource() {
        return source;
    }

    public Class<?> getTarget() {
        return target;
    }
}
