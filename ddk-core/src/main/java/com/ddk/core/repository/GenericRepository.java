package com.ddk.core.repository;

import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;

import java.util.List;

/**
 * 通用仓储接口（纯抽象，不依赖任何持久层框架）
 * <p>
 * 泛型顺序是 {@code <E, ID>}——实体在前、标识在后，与 Spring Data 的习惯一致。
 * 早期版本声明为 {@code <ID, E>}，和 javadoc 写的顺序相反，容易把两个参数传反。
 * <p>
 * 这里<b>刻意不</b>把 {@code E} 约束成 {@code AggregateRoot}：DDK 的领域模型基类是
 * 「可以只用一部分」的，把仓储绑死在聚合根上会让还没引入领域模型的项目无法使用它。
 * 需要在保存后发布领域事件的场景，见 {@code com.ddk.core.domain.DomainEventPublisher}。
 *
 * @param <E>  实体类型
 * @param <ID> 标识类型
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface GenericRepository<E, ID> {

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
