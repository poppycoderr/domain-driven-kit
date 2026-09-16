package com.example.app.domain.model.valueobject;

import com.ddk.core.domain.ValueObject;

import java.util.regex.Pattern;

/**
 * 手机号值对象。
 * <p>
 * 用 record 实现值对象：不可变、按值相等、构造期自我校验，三条约束一次满足。
 * 格式校验放在这里而不是 Command 的 {@code @Pattern} 上——
 * 注解只能拦住 HTTP 入口，值对象能拦住所有构造路径。
 *
 * @author Elijah Du
 */
public record PhoneNumber(String value) implements ValueObject {

    private static final Pattern PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    public PhoneNumber {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("手机号格式非法：" + value);
        }
    }

    /**
     * 脱敏展示，用于日志与对外响应。
     */
    public String masked() {
        return value.substring(0, 3) + "****" + value.substring(7);
    }
}
