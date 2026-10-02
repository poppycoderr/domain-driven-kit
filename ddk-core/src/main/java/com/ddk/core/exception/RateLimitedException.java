package com.ddk.core.exception;

/**
 * 调用超过了限流阈值。Web 层映射为 429 Too Many Requests。
 */
public class RateLimitedException extends BusinessException {

    public RateLimitedException() {
        super(CommonError.RATE_LIMITED);
    }
}
