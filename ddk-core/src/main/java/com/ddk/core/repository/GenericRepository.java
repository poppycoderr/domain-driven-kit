package com.ddk.core.repository;

import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;

import java.util.List;

/**
 * 通用仓储接口（纯抽象，不依赖任何持久层框架）
 *
 * @param <E>  实体类型
 * @param <ID> 主键类型
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface GenericRepository<ID, E> {

    /**
     * 保存实体
     */
    boolean create(E entity);

    /**
     * 批量保存实体
     */
    boolean create(List<E> entities);

    /**
     * 根据主键查询实体
     */
    E find(ID id);

    /**
     * 根据主键列表查询实体
     */
    List<E> find(List<ID> ids);

    /**
     * 根据主键删除实体
     */
    boolean remove(ID id);

    /**
     * 根据主键列表删除实体
     */
    boolean remove(List<ID> ids);

    /**
     * 更新实体
     */
    boolean update(E entity);

    /**
     * 批量更新实体
     */
    boolean update(List<E> entities);

    /**
     * 分页查询
     */
    PageResponse<E> page(PageQuery pageQuery);

    /**
     * 查询实体数量
     */
    long count();

    /**
     * 判断实体是否存在
     */
    boolean existsById(ID id);
}
