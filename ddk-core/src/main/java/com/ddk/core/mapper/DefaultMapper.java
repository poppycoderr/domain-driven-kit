package com.ddk.core.mapper;

import org.mapstruct.Mapper;

/**
 * 默认映射器
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
@Mapper(config = MapperConfiguration.class)
public interface DefaultMapper extends ObjectMapper<Object, Object> {
}
