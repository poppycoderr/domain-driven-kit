package com.ddk.mybatis.starter.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.MapperProvider;
import com.ddk.core.mapper.MissingMapperException;
import com.ddk.core.mapper.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MyBatis starter 自动装配")
class MybatisPlusAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class));

    record Source(String name) {
    }

    record Target(String name) {
    }

    @EnhancedMapper(source = Source.class, target = Target.class)
    static class SourceToTargetMapper implements ObjectMapper<Source, Target> {
        @Override
        public Target map(Source source) {
            return new Target(source.name());
        }

        @Override
        public List<Target> map(List<Source> sources) {
            return sources.stream().map(this::map).toList();
        }
    }

    @Configuration
    static class MapperConfig {
        @Bean
        SourceToTargetMapper sourceToTargetMapper() {
            return new SourceToTargetMapper();
        }
    }

    @Test
    @DisplayName("装配分页插件、ID 生成器与自动填充处理器")
    void registersMybatisPlusBeans() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(MybatisPlusInterceptor.class)
                .hasSingleBean(IdentifierGenerator.class)
                .hasSingleBean(MetaObjectHandler.class));
    }

    @Test
    @DisplayName("MapperProvider 由自动配置注册，不依赖使用方扫描 com.ddk 包")
    void registersMapperProvider() {
        runner.run(context -> assertThat(context).hasSingleBean(MapperProvider.class));
    }

    @Test
    @DisplayName("扫描发生在所有单例就绪之后，能拿到同一上下文里注册的映射器")
    void collectsMappersAfterSingletonsReady() {
        runner.withUserConfiguration(MapperConfig.class).run(context -> {
            MapperProvider provider = context.getBean(MapperProvider.class);

            assertThat(provider.contains(Source.class, Target.class)).isTrue();
            assertThat(provider.lookup(Source.class, Target.class).map(new Source("a")))
                    .isEqualTo(new Target("a"));
            assertThatThrownBy(() -> provider.lookup(Target.class, Source.class))
                    .isInstanceOf(MissingMapperException.class);
        });
    }

    @Test
    @DisplayName("使用方自定义的 Bean 优先")
    void userBeansWin() {
        runner.withBean(IdentifierGenerator.class, () -> param -> 42L)
                .run(context -> assertThat(context.getBean(IdentifierGenerator.class).nextId(null))
                        .isEqualTo(42L));
    }
}
