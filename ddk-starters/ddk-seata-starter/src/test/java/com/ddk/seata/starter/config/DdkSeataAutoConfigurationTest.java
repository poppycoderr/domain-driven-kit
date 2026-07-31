package com.ddk.seata.starter.config;

import org.apache.seata.spring.annotation.GlobalTransactionScanner;
import org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration;
import org.apache.seata.spring.boot.autoconfigure.SeataCoreAutoConfiguration;
import org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本测试只覆盖 DDK 自己的装配条件，不验证 Seata 内部 Bean。三点原因，避免后续维护者重复踩坑：
 *
 * <p>1. Seata 捐给 Apache 后包名从 {@code io.seata} 迁到 {@code org.apache.seata}。
 * seata-all 里仍保留了部分 {@code io.seata} 兼容类，但自动配置注册的是
 * {@code org.apache.seata} 下的实例，断言必须用后者。
 *
 * <p>2. {@link ApplicationContextRunner} 不读取 classpath 上的
 * {@code AutoConfiguration.imports}，只处理显式传入的自动配置类。
 *
 * <p>3. 更关键的是，Seata 的 {@code GlobalTransactionScanner} 依赖由
 * {@code spring.factories} 注册的 {@code ApplicationContextInitializer} 来初始化全局
 * Environment 持有者，而 {@code ApplicationContextRunner} 不会执行这些初始化器，
 * 直接实例化会抛 {@code NullPointerException: ... "environment" is null}。
 * 因此「Seata 核心 Bean 是否正确注册」必须用 {@code @SpringBootTest} 做集成测试验证，
 * 数据源代理（{@code DataSourceProxy}）同理，还需要一个可达的 TC 服务。
 * 这部分留给集成测试补充，不在单元测试里假装能验证。
 */
class DdkSeataAutoConfigurationTest {

    private final ApplicationContextRunner ddkOnly = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkSeataAutoConfiguration.class));

    private final ApplicationContextRunner withSeata = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SeataCoreAutoConfiguration.class,
                    SeataAutoConfiguration.class,
                    SeataDataSourceAutoConfiguration.class,
                    DdkSeataAutoConfiguration.class));

    @Test
    void 未配置时默认生效() {
        ddkOnly.run(context ->
                assertThat(context).hasSingleBean(DdkSeataAutoConfiguration.class));
    }

    @Test
    void 显式开启时生效() {
        ddkOnly.withPropertyValues("seata.enabled=true").run(context ->
                assertThat(context).hasSingleBean(DdkSeataAutoConfiguration.class));
    }

    @Test
    void 显式关闭时退让() {
        // @ConditionalOnProperty(havingValue = "true") 在 enabled=false 时不匹配
        ddkOnly.withPropertyValues("seata.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(DdkSeataAutoConfiguration.class));
    }

    @Test
    void 关闭时Seata自身的Bean也不会被装配() {
        withSeata.withPropertyValues("seata.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DdkSeataAutoConfiguration.class);
            assertThat(context).doesNotHaveBean(GlobalTransactionScanner.class);
        });
    }
}
