package io.github.ddk.core.repository;

import io.github.ddk.core.page.PageQuery;
import io.github.ddk.core.page.PageResponse;

import java.util.List;

/**
 * 通用仓储接口
 *
 * @param <E>  实体类型
 * @param <ID> 主键类型
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface GenericRepository<E, ID> {

    /**
     * 保存实体
     *
     * @param entity 实体
     */
    boolean create(E entity);

    /**
     * 保存实体列表
     *
     * @param entities 实体列表
     */
    boolean create(List<E> entities);

    /**
     * 根据主键查询实体
     *
     * @param id 主键
     * @return 实体
     */
    E find(ID id);

    /**
     * 根据主键列表查询实体
     *
     * @param ids 主键列表
     * @return 实体列表
     */
    List<E> find(List<ID> ids);

    /**
     * 根据主键删除实体
     *
     * @param id 主键
     */
    boolean remove(ID id);

    /**
     * 根据主键列表删除实体
     *
     * @param ids 主键列表
     */
    boolean remove(List<ID> ids);

    /**
     * 更新实体
     *
     * @param entity 实体
     */
    boolean update(E entity);

    /**
     * 更新实体列表
     *
     * @param entities 实体列表
     */
    boolean update(List<E> entities);

    /**
     * 分页查询
     *
     * @param pageQuery 分页查询条件
     * @return 分页结果
     */
    PageResponse<E> page(PageQuery pageQuery);

    /**
     * 查询实体数量
     *
     * @return 实体数量
     */
    long count();

    /**
     * 判断实体是否存在
     *
     * @param id 主键
     */
    boolean existsById(ID id);
}
