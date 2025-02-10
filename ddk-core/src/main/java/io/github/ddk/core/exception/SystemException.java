package io.github.ddk.core.exception;

/**
 * 系统异常
 *
 * @author Elijah Du
 * @date 2025/2/8
 */
public class SystemException extends AbstractException {

    public SystemException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SystemException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public SystemException(ErrorCode errorCode, Throwable t) {
        super(errorCode, t);
    }

    public SystemException(ErrorCode errorCode, Throwable t, Object... args) {
        super(errorCode, t, args);
    }
}
