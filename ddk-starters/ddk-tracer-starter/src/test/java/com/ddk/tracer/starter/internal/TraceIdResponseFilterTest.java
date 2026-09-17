package com.ddk.tracer.starter.internal;

import com.ddk.tracer.starter.TracingAutoConfigurations;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdResponseFilterTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=test-app")
            .withConfiguration(TracingAutoConfigurations.withDdk());

    @Test
    void writesCurrentTraceIdIntoResponseHeader() {
        runner.run(context -> {
            Tracer tracer = context.getBean(Tracer.class);
            TraceIdResponseFilter filter = new TraceIdResponseFilter(tracer, "X-Trace-Id");
            MockHttpServletResponse response = new MockHttpServletResponse();

            Span span = tracer.nextSpan().name("request").start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
            } finally {
                span.end();
            }

            assertThat(response.getHeader("X-Trace-Id"))
                    .isEqualTo(span.context().traceId())
                    .hasSize(32);
        });
    }

    @Test
    void leavesResponseUntouchedWithoutActiveSpan() {
        runner.run(context -> {
            TraceIdResponseFilter filter = new TraceIdResponseFilter(context.getBean(Tracer.class), "X-Trace-Id");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(new MockHttpServletRequest(), response, chain);

            assertThat(response.containsHeader("X-Trace-Id")).isFalse();
            assertThat(chain.getRequest()).as("chain continues").isNotNull();
        });
    }
}
