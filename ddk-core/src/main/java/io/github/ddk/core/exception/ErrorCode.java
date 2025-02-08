package io.github.ddk.core.exception;

import java.text.MessageFormat;

/**
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

    default String getMessage(Object... args) {
        return MessageFormat.format(this.getMessage(), args);
    }
}
