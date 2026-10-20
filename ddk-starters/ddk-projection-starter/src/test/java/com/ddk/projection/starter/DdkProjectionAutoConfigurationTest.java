package com.ddk.projection.starter;

import com.ddk.projection.starter.config.DdkProjectionAutoConfiguration;
import com.ddk.projection.starter.internal.RefreshMetrics;
import com.ddk.projection.starter.internal.RefreshWorker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
    @DisplayName("有 MeterRegistry 时输出积压、失败与刷新耗时指标")
    void publishesMetricsWhenMicrometerIsPresent() {
        runner.withUserConfiguration(OneProjection.class).withBean(SimpleMeterRegistry.class).run(context -> {
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            Projections projections = context.getBean(Projections.class);
            projections.markDirty("catalog", "1");

            await().atMost(Duration.ofSeconds(5)).until(() -> registry.find("ddk.projection.refresh").tag("outcome", "success").timer() != null);
            assertThat(registry.get("ddk.projection.refresh").tag("projection", "catalog").timer().count()).isEqualTo(1);
            assertThat(registry.get("ddk.projection.pending").tag("projection", "catalog").gauge().value()).isZero();
            assertThat(registry.get("ddk.projection.failing").tag("projection", "catalog").gauge().value()).isZero();
            assertThat(registry.get("ddk.projection.oldest.age").tag("projection", "catalog").gauge().value()).isZero();
        });
    }

    @Test
    @DisplayName("没有 Micrometer 时照常装配，只是没有指标")
    void worksWithoutMicrometer() {
        runner.withUserConfiguration(OneProjection.class).withClassLoader(new FilteredClassLoader(MeterRegistry.class)).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Projections.class).doesNotHaveBean(RefreshMetrics.class);
            assertThat(context.getBean(RefreshWorker.class).isRunning()).isTrue();
        });
    }

    @Test
    @DisplayName("应用自己声明的 Projections 优先")
    void userProjectionsWin() {
        runner.withUserConfiguration(OneProjection.class, OwnProjections.class)
                .run(context -> assertThat(context).getBean(Projections.class).isSameAs(OwnProjections.STUB));
    }

    @Test
    @DisplayName("关闭后不注册；关闭自动建表时不建表")
    void canBeTurnedOff() {
        runner.withUserConfiguration(OneProjection.class).withPropertyValues("ddk.projection.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Projections.class));
        runner.withUserConfiguration(OneProjection.class)
                .withPropertyValues("ddk.projection.initialize-schema=false", "ddk.projection.table=never_created")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("Pending refresh table never_created does not exist").hasMessageContaining("CREATE TABLE"));
    }

    @Test
    @DisplayName("两个读模型重名、表名不合法，都让启动失败")
    void invalidSetupsFailAtStartup() {
        runner.withUserConfiguration(OneProjection.class, SameNameAgain.class).run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("Two projections are named catalog"));
        runner.withUserConfiguration(OneProjection.class).withPropertyValues("ddk.projection.table=pending; DROP TABLE x")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("Invalid pending refresh table name"));
    }

    @Test
    @DisplayName("非法的批量大小、间隔、租约和退避组合让启动失败，而不是让后台线程空转")
    void invalidSettingsFailAtStartup() {
        assertStartupFails("ddk.projection.batch-size=0", "batch-size must be greater than 0");
        assertStartupFails("ddk.projection.batch-size=-5", "batch-size must be greater than 0");
        assertStartupFails("ddk.projection.sweep-interval=0s", "sweep-interval must be a positive duration");
        assertStartupFails("ddk.projection.retry-delay=-1s", "retry-delay must be a positive duration");
        assertStartupFails("ddk.projection.lease-time=0s", "lease-time must be a positive duration");
        assertStartupFails("ddk.projection.retry-delay=10m", "must not exceed ddk.projection.max-retry-delay");
    }

    private void assertStartupFails(String property, String message) {
        runner.withUserConfiguration(OneProjection.class).withPropertyValues(property)
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining(message));
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

    @Configuration(proxyBeanMethods = false)
    static class OwnProjections {

        static final Projections STUB = new Projections() {

            @Override
            public void markDirty(String projection, Object id) {
            }

            @Override
            public long rebuild(String projection) {
                return 0;
            }

            @Override
            public long pending(String projection) {
                return 0;
            }

            @Override
            public List<FailedRefresh> failures(String projection, int limit) {
                return List.of();
            }

            @Override
            public int retryFailed(String projection) {
                return 0;
            }
        };

        @Bean
        Projections projections() {
            return STUB;
        }
    }
}
