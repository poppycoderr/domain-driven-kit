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

    CUSTOMER_REQUIRED("缺少顾客身份：开发模式下在请求头 X-Customer-Id 中传入顾客 ID，secure profile 下以顾客身份登录"),

    ACTING_FOR_OTHER_CUSTOMER("无权代顾客 {0} 操作，只有客服可以"),

    SUPPORT_REQUIRED("只有客服可以使用这个功能"),
    ;

    private final String message;
}
