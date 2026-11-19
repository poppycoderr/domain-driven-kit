package com.ddk.mcp.starter.internal;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.Ordered;

/**
 * 为声明了 {@code @McpTool} 方法的 Bean 创建代理并挂上调用拦截。
 * <p>
 * Spring AI 的注解扫描器在 {@code postProcessAfterInitialization} 中登记 Bean，且没有实现 {@link Ordered}，
 * 会排在本处理器之后执行，登记到 MCP server 的因此是代理对象。使用 CGLIB 代理：扫描器按目标类上的
 * {@code Method} 反射调用，只有子类代理才能让这次调用经过拦截。
 * <p>
 * 本处理器和 concurrency starter 的处理器都把拦截放在已有通知之前，后执行的那个在最外层。两者顺序值相同，
 * 自动配置排在 concurrency 之后，本处理器因此后执行：先校验入参，再登记 {@code @Idempotent}、加锁、限流，
 * 它们抛出的重复请求、聚合忙、限流异常也经过这里翻译成错误码并记入审计日志。
 */
public class McpToolAdvisingPostProcessor extends AbstractBeanFactoryAwareAdvisingPostProcessor {

    public McpToolAdvisingPostProcessor(MethodInterceptor interceptor) {
        this.advisor = new DefaultPointcutAdvisor(AnnotationMatchingPointcut.forMethodAnnotation(McpTool.class), interceptor);
        setProxyTargetClass(true);
        setBeforeExistingAdvisors(true);
        setOrder(Ordered.LOWEST_PRECEDENCE);
    }
}
