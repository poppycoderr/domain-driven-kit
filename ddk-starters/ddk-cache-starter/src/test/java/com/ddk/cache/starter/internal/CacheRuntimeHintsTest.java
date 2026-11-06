package com.ddk.cache.starter.internal;

import com.ddk.cache.starter.config.DdkCacheProperties;
import com.ddk.cache.starter.invalidation.CacheInvalidationPublisher;
import com.ddk.cache.starter.metrics.CacheMetrics;
import com.ddk.cache.starter.support.DdkCacheManager;
import com.ddk.cache.starter.support.TwoLevelCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("原生镜像的反射提示：Caffeine")
class CacheRuntimeHintsTest {

    @Test
    @DisplayName("登记了一级缓存实际用到的 Caffeine 缓存类和节点类的构造器和字段")
    void registersTheConstructorsCaffeineLooksUp() {
        RuntimeHints hints = new RuntimeHints();
        new CacheRuntimeHints().registerHints(hints, getClass().getClassLoader());

        for (String name : CacheRuntimeHints.CAFFEINE_CLASSES) {
            assertThat(RuntimeHintsPredicates.reflection().onType(TypeReference.of(name))
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS)).accepts(hints);
        }
    }

    @Test
    @DisplayName("登记了失效广播消息的 record 组件，Jackson 在原生镜像里才能读写它")
    void registersTheInvalidationMessageForJackson() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new CacheRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(CacheInvalidationMessage.class)).accepts(hints);
        for (String component : new String[] {"origin", "cacheName", "key"}) {
            assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(CacheInvalidationMessage.class.getDeclaredMethod(component)))
                    .accepts(hints);
        }
    }

    @Test
    @DisplayName("登记的正是一级缓存构建出来的类：改了构建参数或升级 Caffeine 后类名变了，这里会失败")
    void theHintedClassesAreTheOnesTheLocalCacheUses() throws Exception {
        DdkCacheProperties properties = new DdkCacheProperties();
        properties.getLocal().setEnabled(true);
        DdkCacheManager manager = new DdkCacheManager(properties, null, new DefaultConversionService(), CacheInvalidationPublisher.NOOP,
                CacheMetrics.NOOP);
        TwoLevelCache cache = (TwoLevelCache) manager.getCache("products");
        cache.put("sku-1", "first");

        Object local = field(cache, "local");
        Object bounded = field(local, "cache");
        Object node = ((Map<?, ?>) field(bounded, "data")).values().iterator().next();

        assertThat(CacheRuntimeHints.CAFFEINE_CLASSES).containsExactly(bounded.getClass().getName(), node.getClass().getName());
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException e) {
                // 在父类上找
            }
        }
        throw new NoSuchFieldException(name);
    }
}
