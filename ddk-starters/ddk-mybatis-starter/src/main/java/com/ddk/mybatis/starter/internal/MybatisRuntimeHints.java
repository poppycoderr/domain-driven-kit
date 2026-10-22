package com.ddk.mybatis.starter.internal;

import cn.hutool.core.lang.Snowflake;
import org.jspecify.annotations.Nullable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM 原生镜像需要、但依赖库自己没有登记的反射。
 * <p>
 * MyBatis-Plus 的 {@code BaseMapper.selectOne} 等默认方法通过反射读取 {@link SqlSessionTemplate} 的
 * {@code sqlSessionFactory}；默认的雪花 ID 生成器经 Hutool 的单例工具用反射创建 {@link Snowflake}。
 * 缺少这两项时，原生镜像里条件查询和插入会失败，而按主键的读写不受影响。
 */
public class MybatisRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.reflection().registerType(SqlSessionTemplate.class, MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(Snowflake.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
    }
}
