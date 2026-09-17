package com.ddk.tracer.starter.config;

import com.ddk.tracer.starter.TracingAutoConfigurations;
import com.ddk.tracer.starter.internal.TraceIdResponseFilter;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DdkTracerAutoConfigurationTest {

    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withPropertyValues("spring.application.name=test-app")
            .withConfiguration(TracingAutoConfigurations.withDdk());

    @Test
    void registersTraceIdFilterRightAfterObservationFilter() {
        web.run(context -> {
            assertThat(context).hasSingleBean(Tracer.class);
            FilterRegistrationBean<?> registration = context.getBean("ddkTraceIdResponseFilter", FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(TraceIdResponseFilter.class);
            assertThat(registration.getOrder()).isEqualTo(DdkTracerAutoConfiguration.TRACE_ID_FILTER_ORDER);
        });
    }

    @Test
    void usesConfiguredHeaderName() {
        web.withPropertyValues("ddk.tracer.response-header.name=X-Request-Trace").run(context -> {
            Tracer tracer = context.getBean(Tracer.class);
            TraceIdResponseFilter filter = (TraceIdResponseFilter) context
                    .getBean("ddkTraceIdResponseFilter", FilterRegistrationBean.class).getFilter();
            MockHttpServletResponse response = new MockHttpServletResponse();

            var span = tracer.nextSpan().start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
            } finally {
                span.end();
            }

            assertThat(response.getHeader("X-Request-Trace")).isEqualTo(span.context().traceId());
        });
    }

    @Test
    void skipsFilterWhenDisabled() {
        web.withPropertyValues("ddk.tracer.response-header.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("ddkTraceIdResponseFilter"));
    }

    @Test
    void skipsFilterOutsideServletApplications() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.application.name=test-app")
                .withConfiguration(TracingAutoConfigurations.withDdk())
                .run(context -> {
                    assertThat(context).hasSingleBean(DdkTracerProperties.class);
                    assertThat(context).doesNotHaveBean("ddkTraceIdResponseFilter");
                });
    }
}
