package com.ddk.core.mapper;

import java.util.List;

/**
 * 两种对象之间的映射，例如实体与持久化对象。
 * <p>
 * 1.0 之前叫 {@code ObjectMapper}，与 Jackson 的 {@code ObjectMapper} 同名，两者同时出现的类里只能写全限定名，所以改了名。
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface BeanMapper<S, T> {

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
