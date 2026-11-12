package com.ddk.core.mapper.enums;

import org.jspecify.annotations.Nullable;

/**
 * 枚举与持久化值之间的映射，供 MapStruct 映射器通过 {@code uses} 引用。
 * <p>
 * 读到代码不认识的值时失败，而不是返回 {@code null}：静默的 {@code null} 会在别处变成空指针或错误的业务判断。
 * 滚动升级时这意味着，新增一个枚举值之前，所有实例都要先升级到认识它的版本，再开始写入它。
 *
 * @author Elijah Du
 * @date 2025/2/12
 */
public class EnumMapper {

    /**
     * 从值到枚举的转换。
     * <p>
     * 比较用 {@code equals}，所以值的类型也要一致：列是 {@code INT} 而 {@link IEnum#getValue()} 返回 {@code Long} 时同样匹配不上。
     *
     * @param value 持久化的值；为 {@code null} 时返回 {@code null}
     * @param type  枚举类
     * @param <T>   枚举类型
     * @return 枚举
     * @throws IllegalArgumentException 没有哪个枚举常量的值等于 {@code value}
     */
    public <T extends Enum<T> & IEnum> @Nullable T toEnum(@Nullable Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        for (T constant : type.getEnumConstants()) {
            if (value.equals(constant.getValue())) {
                return constant;
            }
        }
        throw new IllegalArgumentException("No constant of " + type.getName() + " has the value " + value
                + " (" + value.getClass().getName() + ")");
    }

    /**
     * 从枚举到值的转换
     *
     * @param instance 枚举
     * @param <T>      枚举类型
     * @return 值
     */
    public <T extends Enum<T> & IEnum> @Nullable Object toValue(@Nullable T instance) {
        if (instance == null) {
            return null;
        }
        return instance.getValue();
    }
}
