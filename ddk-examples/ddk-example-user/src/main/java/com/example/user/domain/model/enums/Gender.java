package com.example.user.domain.model.enums;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.mapper.enums.IEnum;
import com.example.user.domain.error.UserError;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 性别。落库与对外传输都使用 {@link #code}。
 */
@Getter
@AllArgsConstructor
public enum Gender implements IEnum {

    MALE(0, "male"),
    FEMALE(1, "female"),
    ;

    private final int code;
    private final String description;

    public static Gender of(Integer code) {
        return Arrays.stream(values()).filter(gender -> code != null && gender.code == code).findFirst()
                .orElseThrow(() -> new BusinessException(UserError.INVALID_GENDER, code));
    }

    @Override
    public Object getValue() {
        return code;
    }
}
