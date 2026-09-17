package com.ddk.mcp.starter.internal;

/**
 * 交给 Spring AI 的工具错误。消息以错误码开头（如 {@code USER_NOT_FOUND: 用户不存在：42}），模型可以据此判断是否重试或换一种做法。
 */
public class McpToolException extends RuntimeException {

    public McpToolException(String message) {
        super(message, null, false, false);
    }
}
