package com.ddk.concurrency.starter.internal;

import com.ddk.core.domain.Identifier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对方法参数求值注解里的 SpEL 表达式。表达式来自源码里的注解而不是外部输入；解析结果按表达式文本缓存。
 */
public class KeyExpressions {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    /**
     * 参数数组直接从调用交给求值上下文，中间不声明它的类型：数组元素的可空标注在不同版本的 JDK 编译器里读到的结果不一样。
     *
     * @throws IllegalStateException 表达式的结果为空
     */
    public String evaluate(String expression, Method method, Object target, MethodInvocation invocation) {
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(target, method, invocation.getArguments(), parameterNames);
        Object value = expressions.computeIfAbsent(expression, parser::parseExpression).getValue(context);
        if (value instanceof Identifier<?> identifier) {
            value = identifier.value();
        }
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Expression \"" + expression + "\" on " + method.getDeclaringClass().getSimpleName() + "."
                    + method.getName() + " evaluated to an empty key");
        }
        return value.toString();
    }
}
