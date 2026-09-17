package com.ddk.mcp.starter.config;

import com.ddk.mcp.starter.internal.McpToolAdvisingPostProcessor;
import com.ddk.mcp.starter.internal.McpToolInvocationInterceptor;
import jakarta.validation.Validator;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

/**
 * MCP 工具的 DDK 约定。
 * <p>
 * MCP server 与 {@code @McpTool} 的发现由 Spring AI 完成；这里只为工具方法加上 DDK 的调用拦截：
 * 按参数上的约束注解校验入参、把业务异常翻译成带错误码的工具错误、隐藏未预期异常的内部细节、记录审计日志。
 * <p>
 * 工具类应放在适配层（例如 {@code adapter.mcp}），像控制器一样只调用应用服务，
 * 由 {@code CommonArchRules.MCP_TOOLS_MUST_RESIDE_IN_ADAPTER} 在架构测试中约束。
 */
@AutoConfiguration(after = ValidationAutoConfiguration.class)
@ConditionalOnClass(McpTool.class)
@ConditionalOnProperty(prefix = DdkMcpProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DdkMcpProperties.class)
public class DdkMcpAutoConfiguration {

    /**
     * {@code BeanPostProcessor} 必须是 static 工厂方法，并且不能在创建时拉起其他 Bean：
     * 属性从 {@link Environment} 直接绑定，{@link Validator} 延迟到首次调用时再获取。
     */
    @Bean
    static McpToolAdvisingPostProcessor ddkMcpToolAdvisingPostProcessor(Environment environment, @Lazy ObjectProvider<Validator> validator) {
        boolean validation = environment.getProperty(DdkMcpProperties.PREFIX + ".validation", Boolean.class, true);
        boolean auditLog = environment.getProperty(DdkMcpProperties.PREFIX + ".audit-log", Boolean.class, true);
        return new McpToolAdvisingPostProcessor(new McpToolInvocationInterceptor(validation ? validator::getIfAvailable : () -> null, auditLog));
    }
}
