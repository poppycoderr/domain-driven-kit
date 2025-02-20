package io.github.ddk.core.mapper;

import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface ObjectMapper<S, T> {

    T map(S source);

    List<T> map(List<S> sources);
}
