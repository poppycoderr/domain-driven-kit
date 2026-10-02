package com.ddk.web.internal;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.web.context.OperatorResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求期间把 {@link OperatorResolver} 解析出的操作者放进 {@link OperatorContext}，结束时清除。
 * <p>
 * 请求线程来自线程池，不清除的话上一个请求的操作者会留给下一个请求。
 */
public class OperatorContextFilter extends OncePerRequestFilter {

    private final OperatorResolver resolver;

    public OperatorContextFilter(OperatorResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Operator operator = resolver.resolve(request);
        if (operator == null) {
            chain.doFilter(request, response);
            return;
        }
        OperatorContext.set(operator);
        try {
            chain.doFilter(request, response);
        } finally {
            OperatorContext.clear();
        }
    }
}
