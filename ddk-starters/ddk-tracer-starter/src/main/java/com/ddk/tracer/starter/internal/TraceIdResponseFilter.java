package com.ddk.tracer.starter.internal;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把当前 span 的 traceId 写进响应头。
 * <p>
 * 必须排在 Spring Boot 的 {@code ServerHttpObservationFilter} 之后，那时请求的 span 才已经开启；
 * 并且在调用后续过滤器链之前写头——响应一旦提交，再设置的头会被静默丢弃。
 *
 * @author Elijah Du
 */
public class TraceIdResponseFilter extends OncePerRequestFilter {

    private final Tracer tracer;
    private final String headerName;

    public TraceIdResponseFilter(Tracer tracer, String headerName) {
        this.tracer = tracer;
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Span span = tracer.currentSpan();
        if (span != null && !response.containsHeader(headerName)) {
            response.setHeader(headerName, span.context().traceId());
        }
        chain.doFilter(request, response);
    }
}
