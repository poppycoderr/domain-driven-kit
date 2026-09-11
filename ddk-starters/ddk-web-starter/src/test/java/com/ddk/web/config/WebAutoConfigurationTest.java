package com.ddk.web.config;

import com.ddk.web.handler.BaseExceptionHandler;
import com.ddk.web.properties.DdkWebProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Web starter 自动装配")
class WebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    @DisplayName("默认注册异常处理器与 Jackson customizer")
    void registersDefaults() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(BaseExceptionHandler.class)
                .hasSingleBean(Jackson2ObjectMapperBuilderCustomizer.class));
    }

    @Test
    @DisplayName("跨域默认关闭——库不该替使用方开全域跨域")
    void corsIsDisabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean("ddkCorsConfigurer"));
    }

    @Test
    @DisplayName("显式打开后才注册跨域配置")
    void corsCanBeEnabled() {
        runner.withPropertyValues(
                        "ddk.web.cors.enabled=true",
                        "ddk.web.cors.allowed-origins=https://app.example.com")
                .run(context -> assertThat(context).hasBean("ddkCorsConfigurer")
                        .getBean("ddkCorsConfigurer").isInstanceOf(WebMvcConfigurer.class));
    }

    @Test
    @DisplayName("allow-credentials 配合 allowed-origins=* 时启动失败，而不是留到运行期被浏览器拒绝")
    void rejectsWildcardOriginWithCredentials() {
        runner.withPropertyValues(
                        "ddk.web.cors.enabled=true",
                        "ddk.web.cors.allowed-origins=*",
                        "ddk.web.cors.allow-credentials=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("allow-credentials"));
    }

    @Test
    @DisplayName("异常处理器与 Jackson 定制都可以单独关掉")
    void featuresCanBeDisabled() {
        runner.withPropertyValues("ddk.web.exception-handler=false", "ddk.web.jackson=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(BaseExceptionHandler.class)
                        .doesNotHaveBean(Jackson2ObjectMapperBuilderCustomizer.class));
    }

    @Test
    @DisplayName("使用方自定义的异常处理器优先")
    void userBeanWins() {
        BaseExceptionHandler custom = new BaseExceptionHandler() {
        };
        runner.withBean(BaseExceptionHandler.class, () -> custom)
                .run(context -> assertThat(context.getBean(BaseExceptionHandler.class)).isSameAs(custom));
    }

    @Test
    @DisplayName("配置项绑定到 DdkWebProperties")
    void bindsProperties() {
        runner.withPropertyValues(
                        "ddk.web.cors.enabled=true",
                        "ddk.web.cors.path-pattern=/api/**",
                        "ddk.web.cors.allowed-origins=https://a.example.com,https://b.example.com",
                        "ddk.web.cors.max-age=30m")
                .run(context -> {
                    DdkWebProperties properties = context.getBean(DdkWebProperties.class);
                    assertThat(properties.getCors().getPathPattern()).isEqualTo("/api/**");
                    assertThat(properties.getCors().getAllowedOrigins())
                            .containsExactly("https://a.example.com", "https://b.example.com");
                    assertThat(properties.getCors().getMaxAge().toMinutes()).isEqualTo(30);
                });
    }
}
