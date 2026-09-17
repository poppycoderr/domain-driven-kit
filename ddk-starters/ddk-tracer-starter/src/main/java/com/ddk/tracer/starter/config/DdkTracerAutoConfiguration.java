package com.ddk.tracer.starter.config;

import com.ddk.tracer.starter.web.TraceIdResponseFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路追踪自动配置。
 * <p>
 * Tracer、上下文传播、OTLP 导出都由 Spring Boot actuator 的自动配置提供，
 * 这里只补上 DDK 自己的能力：把 traceId 返回给调用方。
 *
 * @author Elijah Du
 */
@AutoConfiguration(after = MicrometerTracingAutoConfiguration.class)
@ConditionalOnClass(Tracer.class)
@EnableConfigurationProperties(DdkTracerProperties.class)
public class DdkTracerAutoConfiguration {

    /**
     * Boot 的 {@code ServerHttpObservationFilter} 排在 {@code HIGHEST_PRECEDENCE + 1}，本过滤器紧随其后。
     */
    public static final int TRACE_ID_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnBean(Tracer.class)
    @ConditionalOnProperty(prefix = DdkTracerProperties.PREFIX, name = "response-header.enabled",
            havingValue = "true", matchIfMissing = true)
    static class ResponseHeaderConfiguration {

        @Bean
        FilterRegistrationBean<TraceIdResponseFilter> ddkTraceIdResponseFilter(Tracer tracer,
                                                                               DdkTracerProperties properties) {
            FilterRegistrationBean<TraceIdResponseFilter> registration = new FilterRegistrationBean<>(
                    new TraceIdResponseFilter(tracer, properties.getResponseHeader().getName()));
            registration.setOrder(TRACE_ID_FILTER_ORDER);
            return registration;
        }
    }
}
