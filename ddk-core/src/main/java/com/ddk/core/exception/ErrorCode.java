package com.ddk.core.exception;

import java.text.MessageFormat;

/**
 * 错误码接口
 *
 * @author Elijah Du
 * @date 2025/2/7
 */
public interface ErrorCode {

    /**
     * 错误信息
     *
     * @return 错误信息
     */
    String getMessage();

    default String getCode() {
        return this.toString();
    }

    /**
     * 用 {@link MessageFormat} 填充消息里的 {@code {0}} 占位符。
     * <p>
     * 不传参数时直接返回原始消息：{@code MessageFormat} 遇到 null 参数数组会抛
     * {@code NullPointerException}，而「没有参数」是完全正常的调用方式。
     */
    default String getMessage(Object... args) {
        if (args == null || args.length == 0) {
            return getMessage();
        }
        return MessageFormat.format(this.getMessage(), args);
    }
}
