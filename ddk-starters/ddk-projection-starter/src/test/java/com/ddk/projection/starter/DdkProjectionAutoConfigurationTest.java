package com.ddk.projection.starter;

import com.ddk.projection.starter.config.DdkProjectionAutoConfiguration;
import com.ddk.projection.starter.internal.RefreshWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("读模型投影自动装配")
class DdkProjectionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, DdkProjectionAutoConfiguration.class))
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:projection-config;DB_CLOSE_DELAY=-1");

    @Test
    @DisplayName("应用里没有读模型时什么都不注册，也不建表")
    void nothingWithoutProjections() {
        runner.run(context -> assertThat(context).doesNotHaveBean(Projections.class).doesNotHaveBean(RefreshWorker.class));
    }

    @Test
    @DisplayName("有读模型时注册入口和后台处理，并按配置的表名建表")
    void registersEverythingAndCreatesTheTable() {
        runner.withUserConfiguration(OneProjection.class).withPropertyValues("ddk.projection.table=read_model_pending").run(context -> {
            assertThat(context).hasSingleBean(Projections.class).hasSingleBean(RefreshWorker.class);
            assertThat(context.getBean(RefreshWorker.class).isRunning()).isTrue();
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM read_model_pending", Long.class)).isZero();
        });
    }

    @Test
    @DisplayName("关闭后不注册；关闭自动建表时不建表")
    void canBeTurnedOff() {
        runner.withUserConfiguration(OneProjection.class).withPropertyValues("ddk.projection.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Projections.class));
        runner.withUserConfiguration(OneProjection.class)
                .withPropertyValues("ddk.projection.initialize-schema=false", "ddk.projection.table=never_created")
                .run(context -> assertThat(context.getBean(JdbcTemplate.class)
                        .queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'NEVER_CREATED'", Long.class)).isZero());
    }

    @Test
    @DisplayName("两个读模型重名、表名不合法，都让启动失败")
    void invalidSetupsFailAtStartup() {
        runner.withUserConfiguration(OneProjection.class, SameNameAgain.class).run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("Two projections are named catalog"));
        runner.withUserConfiguration(OneProjection.class).withPropertyValues("ddk.projection.table=pending; DROP TABLE x")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("Invalid pending refresh table name"));
    }

    @Configuration(proxyBeanMethods = false)
    static class OneProjection {

        @Bean
        Projection catalog() {
            return named("catalog");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SameNameAgain {

        @Bean
        Projection catalogAgain() {
            return named("catalog");
        }
    }

    private static Projection named(String name) {
        return new Projection() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public void refresh(String id) {
            }
        };
    }
}
