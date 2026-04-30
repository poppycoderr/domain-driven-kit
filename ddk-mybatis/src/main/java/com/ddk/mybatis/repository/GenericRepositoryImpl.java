package com.ddk.mybatis.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ddk.core.mapper.MapperProvider;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;
import com.ddk.core.repository.GenericRepository;
import com.ddk.mybatis.page.MybatisPlusPageAdapter;
import com.ddk.mybatis.query.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;

import java.io.Serializable;
import java.util.List;

/**
 * 通用仓储实现（基于 MyBatis-Plus）
 * <p>
 * 自动处理 Entity ↔ PO 之间的映射转换，子类只需声明泛型参数即可获得完整的 CRUD + 分页能力。
 *
 * @param <ID> 主键类型
 * @param <E>  领域实体类型
 * @param <P>  持久化对象类型
 * @param <M>  MyBatis Mapper 类型
 * @author Elijah Du
 * @date 2025/2/11
 */
@SuppressWarnings({"unchecked", "DataFlowIssue"})
public class GenericRepositoryImpl<ID extends Serializable, E, P, M extends BaseMapper<P>>
        extends ServiceImpl<M, P>
        implements GenericRepository<ID, E> {

    protected final Class<?>[] typeArguments = GenericTypeResolver.resolveTypeArguments(this.getClass(), GenericRepositoryImpl.class);
    protected final Class<E> eClass = (Class<E>) typeArguments[1];
    protected final Class<P> pClass = (Class<P>) typeArguments[2];

    @Autowired
    private MapperProvider mapperProvider;

    @Override
    public boolean create(E entity) {
        P po = mapperProvider.lookup(eClass, pClass).map(entity);
        return super.save(po);
    }

    @Override
    public boolean create(List<E> entities) {
        List<P> pos = mapperProvider.lookup(eClass, pClass).map(entities);
        return super.saveBatch(pos);
    }

    @Override
    public E find(ID id) {
        P po = super.getById(id);
        return mapperProvider.lookup(pClass, eClass).map(po);
    }

    @Override
    public List<E> find(List<ID> ids) {
        List<P> pos = super.listByIds(ids);
        return mapperProvider.lookup(pClass, eClass).map(pos);
    }

    @Override
    public boolean remove(ID id) {
        return super.removeById(id);
    }

    @Override
    public boolean remove(List<ID> ids) {
        return super.removeByIds(ids);
    }

    @Override
    public boolean update(E entity) {
        P po = mapperProvider.lookup(eClass, pClass).map(entity);
        return super.updateById(po);
    }

    @Override
    public boolean update(List<E> entities) {
        List<P> pos = mapperProvider.lookup(eClass, pClass).map(entities);
        return super.updateBatchById(pos);
    }

    @Override
    public PageResponse<E> page(PageQuery query) {
        Page<P> page = MybatisPlusPageAdapter.toPage(query);
        super.page(page, QueryParser.parse(query));
        return MybatisPlusPageAdapter.toPageResponse(page, mapperProvider.lookup(pClass, eClass)::map);
    }

    @Override
    public boolean existsById(ID id) {
        return super.getById(id) != null;
    }

    @Override
    public long count() {
        return super.count();
    }
}
