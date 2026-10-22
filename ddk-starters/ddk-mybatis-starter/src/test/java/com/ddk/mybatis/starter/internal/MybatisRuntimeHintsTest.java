package com.ddk.mybatis.starter.internal;

import cn.hutool.core.lang.Snowflake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("原生镜像的反射提示")
class MybatisRuntimeHintsTest {

    @Test
    @DisplayName("登记了条件查询读取 sqlSessionFactory 的方法，以及默认 ID 生成器创建 Snowflake 的构造器")
    void registersWhatMybatisPlusAndHutoolReflectOn() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new MybatisRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(SqlSessionTemplate.class, "getSqlSessionFactory")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(Snowflake.class.getConstructor(long.class, long.class))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(Snowflake.class.getConstructor())).accepts(hints);
    }
}
