package com.ddk.core.exception;

import lombok.Getter;

/**
 * 异常基类
 *
 * @author Elijah Du
 * @date 2025/2/7
 */
@Getter
public abstract class AbstractException extends RuntimeException {

    private final ErrorCode errorCode;

    private final Object[] args;

    public AbstractException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public AbstractException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage(args));
        this.errorCode = errorCode;
        this.args = args;
    }

    public AbstractException(ErrorCode errorCode, Throwable t) {
        super(errorCode.getMessage(), t);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public AbstractException(ErrorCode errorCode, Throwable t, Object... args) {
        super(errorCode.getMessage(args), t);
        this.errorCode = errorCode;
        this.args = args;
    }

}
