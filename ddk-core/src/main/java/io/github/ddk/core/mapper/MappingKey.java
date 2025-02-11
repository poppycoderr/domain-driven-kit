package io.github.ddk.core.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Elijah Du
 * @date 2025/2/11
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
public class MappingKey {

    private final Class<?> sourceClass;
    private final Class<?> targetClass;
}