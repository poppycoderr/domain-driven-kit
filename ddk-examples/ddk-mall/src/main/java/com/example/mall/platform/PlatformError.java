package com.example.mall.platform;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 不属于任何一个上下文的错误码。
 */
@Getter
@AllArgsConstructor
public enum PlatformError implements ErrorCode {

    CUSTOMER_REQUIRED("缺少顾客身份，请在请求头 X-Customer-Id 中传入顾客 ID"),
    ;

    private final String message;
}
