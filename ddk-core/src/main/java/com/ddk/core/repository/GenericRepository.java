package com.ddk.core.repository;

import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;

import java.util.List;
import java.util.Optional;

/**
 * 通用仓储契约（纯抽象，不依赖任何持久层框架）。
 * <p>
 * 泛型顺序是 {@code <E, ID>}——实体在前、标识在后，与 Spring Data 的习惯一致。
 * <p>
 * 这里<b>刻意不</b>把 {@code E} 约束成 {@code AggregateRoot}：DDK 的领域模型基类是
 * 「可以只用一部分」的，把仓储绑死在聚合根上会让还没引入领域模型的项目无法使用它。
 * 但如果 {@code E} 确实是聚合根，实现方应当在写入成功后排空并发布它累积的领域事件。
 *
 * <h2>方法命名</h2>
 * 单个与批量用不同的方法名（{@code find} / {@code findAll}）而不是重载。
 * 重载在 {@code ID} 本身是 {@code List} 时会产生歧义，而且调用点也更难读。
 *
 * @param <E>  实体类型
 * @param <ID> 标识类型
 * @author Elijah Du
 * @date 2025/2/11
 */
public interface GenericRepository<E, ID> {

    /**
     * 保存新实体。
     *
     * @return 保存后的实体。数据库生成标识（自增主键）时，返回的实例才带有标识，
     * 传入的实例不会被就地修改——想拿到标识必须用返回值。
     * 推荐给聚合根预生成标识（雪花 / UUID），这样就没有这个区别。
     */
    E create(E entity);

    /**
     * 批量保存新实体。
     *
     * @return 保存后的实体列表，顺序与入参一致
     */
    List<E> createAll(List<E> entities);

    /**
     * 更新已有实体。
     *
     * @return 更新后的实体
     * @throws com.ddk.core.exception.AbstractException 实现方可在乐观锁冲突时抛出
     */
    E update(E entity);

    /**
     * 批量更新已有实体。
     */
    List<E> updateAll(List<E> entities);

    /**
     * 按标识查询。
     *
     * @return 查不到时返回 {@link Optional#empty()}，而不是 null
     */
    Optional<E> find(ID id);

    /**
     * 按标识列表批量查询。查不到的标识不会出现在结果里，因此返回的列表可能比入参短。
     */
    List<E> findAll(List<ID> ids);

    /**
     * 按标识删除。
     *
     * @return 是否真的删掉了一行。标识不存在时返回 {@code false}，不抛异常
     */
    boolean remove(ID id);

    /**
     * 按标识列表批量删除。
     *
     * @return 实际删除的行数
     */
    long removeAll(List<ID> ids);

    /**
     * 分页查询。
     */
    PageResponse<E> page(PageQuery pageQuery);

    /**
     * 总数。
     */
    long count();

    /**
     * 是否存在。实现方应当用 {@code SELECT 1 ... LIMIT 1} 之类的方式判定，
     * 不要把整行查出来再判空。
     */
    boolean existsById(ID id);
}
