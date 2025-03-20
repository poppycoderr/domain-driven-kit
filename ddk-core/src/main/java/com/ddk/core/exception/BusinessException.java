package com.ddk.core.exception;

/**
 * 业务异常
 *
 * @author Elijah Du
 * @date 2025/2/8
 */
public class BusinessException extends AbstractException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public BusinessException(ErrorCode errorCode, Throwable t) {
        super(errorCode, t);
    }

    public BusinessException(ErrorCode errorCode, Throwable t, Object... args) {
        super(errorCode, t, args);
    }
}
