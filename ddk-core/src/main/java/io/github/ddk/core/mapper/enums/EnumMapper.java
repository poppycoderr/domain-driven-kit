package io.github.ddk.core.mapper.enums;

import java.util.Arrays;

/**
 * 枚举映射器
 *
 * @author Elijah Du
 * @date 2025/2/12
 */
public class EnumMapper {

    /**
     * 从值到枚举的转换
     *
     * @param value 值
     * @param type  枚举类
     * @param <T>   枚举类型
     * @return 枚举
     */
    public <T extends Enum<T> & IEnum> T toEnum(Object value, Class<T> type) {
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.getValue().equals(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从枚举到值的转换
     *
     * @param instance 枚举
     * @param <T>      枚举类型
     * @return 值
     */
    public <T extends Enum<T> & IEnum> Object toValue(T instance) {
        if (instance == null) {
            return null;
        }
        return instance.getValue();
    }
}
