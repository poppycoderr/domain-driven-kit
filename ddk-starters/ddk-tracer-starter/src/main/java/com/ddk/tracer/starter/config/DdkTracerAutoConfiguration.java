package com.ddk.tracer.starter.config;

import com.ddk.tracer.starter.internal.TraceIdResponseFilter;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路追踪自动配置。
 * <p>
 * Tracer、上下文传播、OTLP 导出都由 Spring Boot 的 OpenTelemetry 自动配置提供，
 * 这里只补上 DDK 自己的能力：把 traceId 返回给调用方，并让链路上下文跟随任务进入线程池。
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

    /**
     * Spring Boot 会把容器里的 {@link TaskDecorator} 用到它创建的任务线程池上。没有它，换了线程的工作会各自开一条新链路，
     * 其中就包括事务提交后投递集成事件：一次请求引发的消息会和这次请求断开。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ContextSnapshot.class)
    @ConditionalOnProperty(prefix = DdkTracerProperties.PREFIX, name = "async-propagation.enabled",
            havingValue = "true", matchIfMissing = true)
    static class AsyncPropagationConfiguration {

        @Bean
        @ConditionalOnMissingBean(TaskDecorator.class)
        ContextPropagatingTaskDecorator ddkContextPropagatingTaskDecorator() {
            return new ContextPropagatingTaskDecorator();
        }
    }

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
