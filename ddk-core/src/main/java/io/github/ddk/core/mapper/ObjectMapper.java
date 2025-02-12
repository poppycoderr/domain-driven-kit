package io.github.ddk.core.mapper;

import org.mapstruct.InheritInverseConfiguration;

import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface ObjectMapper<L, R> {

    R mapToRight(L source);

    List<R> mapToRight(List<L> sources);

    @InheritInverseConfiguration
    L mapToLeft(R source);

    List<L> mapToLeft(List<R> sources);
}
