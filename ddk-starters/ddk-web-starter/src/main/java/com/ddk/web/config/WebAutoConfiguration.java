package com.ddk.web.config;

import com.ddk.web.handler.BaseExceptionHandler;
import com.ddk.web.properties.DdkWebProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 自动配置：全局异常处理、Jackson 默认行为、跨域。
 *
 * @author Elijah Du
 */
@Slf4j
@AutoConfiguration(before = {JacksonAutoConfiguration.class, WebMvcAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(DdkWebProperties.class)
public class WebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = DdkWebProperties.PREFIX, name = "exception-handler", matchIfMissing = true)
    public BaseExceptionHandler baseExceptionHandler() {
        return new BaseExceptionHandler();
    }

    /**
     * 以 customizer 的形式参与 Jackson 构建，而不是替换整个 {@code ObjectMapper} Bean。
     * <p>
     * 早期版本直接注册了一个 {@code ObjectMapper} Bean，副作用是使用方在
     * {@code spring.jackson.*} 里写的配置全部失效——Boot 的 {@code JacksonAutoConfiguration}
     * 见到已有同类型 Bean 就退让了。customizer 不会吃掉使用方的定制。
     */
    @Bean
    @ConditionalOnProperty(prefix = DdkWebProperties.PREFIX, name = "jackson", matchIfMissing = true)
    public Jackson2ObjectMapperBuilderCustomizer ddkJacksonCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule())
                // 时间序列化成 ISO-8601 字符串，而不是时间戳数组
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 请求体里多出来的字段忽略掉，而不是直接 400——
                // 否则前端加一个字段就会打挂后端
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * 跨域配置，<b>默认关闭</b>。
     *
     * @throws IllegalStateException 允许携带凭证却把来源写成 {@code *} 时启动失败。
     *                               那等于让任意站点带着用户的 Cookie 调你的接口，
     *                               浏览器也会直接拒绝这个组合——与其运行时才发现，不如启动就报
     */
    @Bean
    @ConditionalOnMissingBean(name = "ddkCorsConfigurer")
    @ConditionalOnProperty(prefix = DdkWebProperties.PREFIX + ".cors", name = "enabled", havingValue = "true")
    public WebMvcConfigurer ddkCorsConfigurer(DdkWebProperties properties) {
        DdkWebProperties.Cors cors = properties.getCors();

        if (cors.isAllowCredentials() && cors.getAllowedOrigins().contains("*")) {
            throw new IllegalStateException(
                    "ddk.web.cors 同时设置了 allow-credentials=true 和 allowed-origins=*。"
                            + "这会让任意站点带着用户的凭证调用你的接口，请显式列出来源。");
        }
        if (cors.getAllowedOrigins().isEmpty()) {
            log.warn("ddk.web.cors.enabled=true 但没有配置 allowed-origins，跨域不会对任何来源生效");
        }

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping(cors.getPathPattern())
                        .allowedOriginPatterns(cors.getAllowedOrigins().toArray(String[]::new))
                        .allowedMethods(cors.getAllowedMethods().toArray(String[]::new))
                        .allowedHeaders(cors.getAllowedHeaders().toArray(String[]::new))
                        .exposedHeaders(cors.getExposedHeaders().toArray(String[]::new))
                        .allowCredentials(cors.isAllowCredentials())
                        .maxAge(cors.getMaxAge().toSeconds());
            }
        };
    }
}
