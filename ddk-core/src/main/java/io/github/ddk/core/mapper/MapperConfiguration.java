package io.github.ddk.core.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * 映射器配置
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public class MapperConfiguration {
}