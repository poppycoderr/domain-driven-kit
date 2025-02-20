package io.github.ddk.domain.model.enums;

import io.github.ddk.core.mapper.enums.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Elijah Du
 * @date 2025/2/20
 */
@Getter
@AllArgsConstructor
public enum Gender implements IEnum {

    Male(0, "male"),
    Female(1,"female");

    private final int gender;
    private final String description;

    @Override
    public Object getValue() {
        return getGender();
    }
}
