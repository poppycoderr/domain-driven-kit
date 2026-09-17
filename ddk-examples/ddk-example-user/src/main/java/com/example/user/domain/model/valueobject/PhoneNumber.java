package com.example.user.domain.model.valueobject;

import com.ddk.core.domain.ValueObject;
import com.ddk.core.exception.BusinessException;
import com.example.user.domain.error.UserError;

import java.util.regex.Pattern;

/**
 * 手机号值对象。格式在构造期校验：请求参数上的注解只拦得住 HTTP 入口，值对象拦得住所有构造路径。
 */
public record PhoneNumber(String value) implements ValueObject {

    private static final Pattern PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    public PhoneNumber {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new BusinessException(UserError.INVALID_PHONE_NUMBER, value);
        }
    }

    public String masked() {
        return value.substring(0, 3) + "****" + value.substring(7);
    }
}
