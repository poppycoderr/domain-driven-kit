package io.github.ddk.core.mapper;

import java.util.List;

/**
 * 对象映射接口
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface ObjectMapper<S, T> {

    /**
     * 映射对象
     *
     * @param source 源对象
     * @return 目标对象
     */
    T map(S source);

    /**
     * 映射对象列表
     *
     * @param sources 源对象列表
     * @return 目标对象列表
     */
    List<T> map(List<S> sources);
}
