package com.example.app.domain.model.valueobject;

import com.ddk.core.domain.ValueObject;

import java.util.regex.Pattern;

/**
 * 邮箱值对象。允许为空（业务上邮箱非必填），但一旦有值就必须合法。
 *
 * @author Elijah Du
 */
public record Email(String value) implements ValueObject {

    private static final Pattern PATTERN = Pattern.compile("^\\w+(\\.\\w+)*@\\w+(\\.\\w+)+$");

    public Email {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("邮箱格式非法：" + value);
        }
    }

    /**
     * 允许缺省的场景用它，避免到处写 null 判断。
     */
    public static Email ofNullable(String value) {
        return value == null || value.isBlank() ? null : new Email(value);
    }
}
