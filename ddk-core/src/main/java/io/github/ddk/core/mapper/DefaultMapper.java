package io.github.ddk.core.mapper;

import org.mapstruct.Mapper;

/**
 * @author Elijah Du
 * @date 2025/2/11
 */
@Mapper(config = MapperConfiguration.class)
public interface DefaultMapper<L, R>  extends ObjectMapper<L, R> {
}
