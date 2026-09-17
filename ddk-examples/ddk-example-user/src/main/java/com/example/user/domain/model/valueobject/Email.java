package com.example.user.domain.model.valueobject;

import com.ddk.core.domain.ValueObject;
import com.ddk.core.exception.BusinessException;
import com.example.user.domain.error.UserError;

import java.util.regex.Pattern;

/**
 * 邮箱值对象。业务上邮箱非必填，但一旦有值就必须合法。
 */
public record Email(String value) implements ValueObject {

    private static final Pattern PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    public Email {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new BusinessException(UserError.INVALID_EMAIL, value);
        }
    }

    public static Email ofNullable(String value) {
        return value == null || value.isBlank() ? null : new Email(value);
    }
}
