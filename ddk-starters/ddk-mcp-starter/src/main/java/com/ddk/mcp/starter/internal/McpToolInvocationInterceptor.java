package com.ddk.mcp.starter.internal;

import com.ddk.core.exception.AbstractException;
import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.CommonError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 包在每个 {@code @McpTool} 方法外的调用拦截：先校验参数，再把异常翻译成带错误码的工具错误，并记录审计日志。
 * <p>
 * 运行时异常会被 Spring AI 转成返回给模型的工具错误，消息原样可见。因此这里只放出可预期的业务信息：
 * {@link AbstractException} 带上错误码与文案，参数校验失败列出字段与原因，其余异常统一为
 * {@code SYSTEM_ERROR}，原始消息只写日志，避免把连接串、类名之类的内部细节交给模型。
 * <p>
 * 抛出的 {@link McpToolException} 刻意不带 cause：Spring AI 生成错误消息时会追溯到异常链的根因，带上原始异常等于把它的消息原样交出去。
 */
@Slf4j
public class McpToolInvocationInterceptor implements MethodInterceptor {

    private final Supplier<@Nullable Validator> validator;

    private final boolean auditLog;

    public McpToolInvocationInterceptor(Supplier<@Nullable Validator> validator, boolean auditLog) {
        this.validator = validator;
        this.auditLog = auditLog;
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        String tool = toolName(invocation.getMethod());
        long start = System.nanoTime();
        try {
            validate(invocation);
            Object result = invocation.proceed();
            audit(tool, "SUCCESS", start);
            return result;
        } catch (ConstraintViolationException e) {
            String message = describe(e.getConstraintViolations());
            audit(tool, CommonError.VALIDATION_ERROR.getCode(), start);
            throw new McpToolException(CommonError.VALIDATION_ERROR.getCode() + ": " + message);
        } catch (AbstractException e) {
            audit(tool, e.getErrorCode().getCode(), start);
            if (!(e instanceof BusinessException)) {
                log.error("MCP tool [{}] failed with [{}]: {}", tool, e.getErrorCode().getCode(), e.getMessage(), e);
            }
            throw new McpToolException(e.getErrorCode().getCode() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            audit(tool, CommonError.SYSTEM_ERROR.getCode(), start);
            log.error("MCP tool [{}] failed unexpectedly: {}", tool, e.getMessage(), e);
            throw new McpToolException(CommonError.SYSTEM_ERROR.getCode() + ": " + CommonError.SYSTEM_ERROR.getMessage());
        }
    }

    private void validate(MethodInvocation invocation) {
        Validator current = validator.get();
        Object target = invocation.getThis();
        if (current == null || target == null) {
            return;
        }
        Set<ConstraintViolation<Object>> violations = current.forExecutables()
                .validateParameters(target, invocation.getMethod(), invocation.getArguments());
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private void audit(String tool, String outcome, long start) {
        if (auditLog) {
            log.info("MCP tool [{}] -> {} in {} ms", tool, outcome, (System.nanoTime() - start) / 1_000_000);
        }
    }

    static String toolName(Method method) {
        McpTool annotation = AnnotatedElementUtils.findMergedAnnotation(method, McpTool.class);
        return annotation != null && StringUtils.hasText(annotation.name()) ? annotation.name() : method.getName();
    }

    private static String describe(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> leafName(v.getPropertyPath()) + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }

    private static String leafName(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                .reduce((first, second) -> second)
                .map(Path.Node::getName)
                .orElse(path.toString());
    }
}
