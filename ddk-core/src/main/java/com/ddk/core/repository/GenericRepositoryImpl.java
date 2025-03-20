package com.ddk.core.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ddk.core.mapper.MapperProvider;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;
import com.ddk.core.page.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;

import java.io.Serializable;
import java.util.List;

/**
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
        Page<P> page = query.page();
        super.page(page, QueryParser.parse(query));
        return PageResponse.of(page, mapperProvider.lookup(pClass, eClass)::map);
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
