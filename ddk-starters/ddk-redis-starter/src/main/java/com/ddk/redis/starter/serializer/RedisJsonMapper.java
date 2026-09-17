package com.ddk.redis.starter.serializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.util.ClassUtils;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 构造写入 Redis 用的 {@link ObjectMapper}。
 *
 * <h2>为什么不能用 LaissezFaireSubTypeValidator</h2>
 * 往 Redis 里存 {@code Object} 需要在 JSON 里带上类名，读回来时按类名实例化。
 * 如果不限制类名，任何能写 Redis 的人都能让应用实例化 classpath 上的任意类——
 * 这正是 Jackson 历年反序列化 RCE 的利用方式。
 * 这里改为白名单：只有 JDK 常用包和显式信任的包里的类型才会被实例化，其余直接拒绝。
 *
 * <h2>为什么不用 DefaultTyping.NON_FINAL</h2>
 * record 是 final 的，{@code NON_FINAL} 不给它写类名，读回来只能得到 {@code LinkedHashMap}。
 * 这里改为：除 JDK 自带的 final 类型（String、Long 等标量）外都写类名。
 *
 * @author Elijah Du
 */
public final class RedisJsonMapper {

    /**
     * 默认放行的 JDK 包。{@code java.net}、{@code java.io} 这类能触发 I/O 的包刻意不在其中。
     */
    public static final List<String> JDK_PACKAGES = List.of("java.lang.", "java.util.", "java.time.", "java.math.");

    private RedisJsonMapper() {
    }

    public static ObjectMapper create(Collection<String> trustedPackages) {
        PolymorphicTypeValidator validator = validator(trustedPackages);
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 按字段存取：领域对象不一定有 getter，且 getter 里可能带计算逻辑
                .changeDefaultVisibility(checker -> checker
                        .withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                        .withVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY))
                .setDefaultTyping(new TypeResolverBuilder(validator))
                .build();
    }

    private static PolymorphicTypeValidator validator(Collection<String> trustedPackages) {
        Set<String> prefixes = new LinkedHashSet<>(JDK_PACKAGES);
        for (String pkg : trustedPackages) {
            if (pkg == null || pkg.isBlank()) {
                continue;
            }
            String trimmed = pkg.strip();
            prefixes.add(trimmed.endsWith(".") ? trimmed : trimmed + ".");
        }
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubTypeIsArray();
        prefixes.forEach(builder::allowIfSubType);
        return builder.build();
    }

    private static final class TypeResolverBuilder extends DefaultTypeResolverBuilder {

        TypeResolverBuilder(PolymorphicTypeValidator validator) {
            super(validator, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY, JsonTypeInfo.Id.CLASS, "@class");
        }

        @Override
        public boolean useForType(JavaType type) {
            if (type.isJavaLangObject()) {
                return true;
            }
            while (type.isArrayType()) {
                type = type.getContentType();
            }
            Class<?> raw = type.getRawClass();
            if (raw.isEnum() || ClassUtils.isPrimitiveOrWrapper(raw) || TreeNode.class.isAssignableFrom(raw)) {
                return false;
            }
            return !(type.isFinal() && raw.getName().startsWith("java."));
        }
    }
}
