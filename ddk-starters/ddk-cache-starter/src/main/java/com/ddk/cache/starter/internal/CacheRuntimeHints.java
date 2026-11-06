package com.ddk.cache.starter.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

import java.util.List;

/**
 * GraalVM 原生镜像需要、但依赖库自己没有登记的反射。
 * <p>
 * Caffeine 按构建参数拼出缓存和节点的类名，再按名字加载，反射读取它们的静态字段并调用构造器。一级缓存只用一种配置
 * （最大条数加自定义 {@code Expiry}），对应缓存类 {@code SSMSA} 和节点类 {@code PSWMS}；
 * 缺少它们时，原生镜像里第一次读写一级缓存就会失败。改动一级缓存的构建参数时，类名会跟着变，测试会指出来。
 * <p>
 * 一级缓存的失效广播 {@link CacheInvalidationMessage} 是 record，Jackson 读写它要用到 record 组件的反射。
 * 缓存里的值是应用自己的类型，它们的反射由应用登记，例如用 {@code @RegisterReflectionForBinding}。
 */
public class CacheRuntimeHints implements RuntimeHintsRegistrar {

    static final List<String> CAFFEINE_CLASSES = List.of(
            "com.github.benmanes.caffeine.cache.SSMSA",
            "com.github.benmanes.caffeine.cache.PSWMS");

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), CacheInvalidationMessage.class);
        for (String name : CAFFEINE_CLASSES) {
            hints.reflection().registerType(TypeReference.of(name), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }
}
