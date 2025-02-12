package io.github.ddk.core.mapper.enums;

/**
 * 枚举接口，通用映射
 *
 * @author Elijah Du
 * @date 2025/2/12
 */
public interface IEnum {

    /**
     * 子类复写该方法，返回枚举值
     */
    Object getValue();
}
