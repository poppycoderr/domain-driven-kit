package com.ddk.core.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct 映射器的共享配置。
 * <p>
 * 在 {@code @Mapper(config = MapperConfiguration.class)} 中引用：生成的映射器注册为 Spring Bean，
 * 目标上没有对应来源的字段不报错，来源字段为 null 时不覆盖目标字段。
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public class MapperConfiguration {
}
