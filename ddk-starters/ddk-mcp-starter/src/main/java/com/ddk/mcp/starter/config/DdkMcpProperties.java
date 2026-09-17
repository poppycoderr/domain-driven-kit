package com.ddk.mcp.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 工具调用的 DDK 配置项。MCP server 本身（端点、协议、名称）仍由 {@code spring.ai.mcp.server.*} 配置。
 */
@Data
@ConfigurationProperties(prefix = DdkMcpProperties.PREFIX)
public class DdkMcpProperties {

    public static final String PREFIX = "ddk.mcp";

    /**
     * 是否为 {@code @McpTool} 方法接入 DDK 的调用拦截：参数校验、错误码转换与审计日志。
     */
    private boolean enabled = true;

    /**
     * 是否按参数上的 Bean Validation 注解校验工具入参，不需要在类上标 {@code @Validated}。
     */
    private boolean validation = true;

    /**
     * 是否为每次工具调用输出一行审计日志：工具名、结果与耗时。出于隐私考虑不记录参数值。
     */
    private boolean auditLog = true;
}
