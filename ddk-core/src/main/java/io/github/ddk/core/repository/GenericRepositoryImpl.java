package io.github.ddk.core.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.ddk.core.mapper.MapperProvider;
import io.github.ddk.core.page.PageQuery;
import io.github.ddk.core.page.PageResponse;
import io.github.ddk.core.page.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/2/11
 */
public class GenericRepositoryImpl<ID extends Serializable, E, P, M extends BaseMapper<P>>
        extends ServiceImpl<M, P>
        implements GenericRepository<ID, E> {

    @Autowired
    private MapperProvider<E, P> mappers;

    @Override
    public boolean create(E entity) {
        P po = mappers.mapToRight(entity);
        return super.save(po);
    }

    @Override
    public boolean create(List<E> entities) {
        List<P> pos = mappers.mapToRight(entities);
        return super.saveBatch(pos);
    }

    @Override
    public E find(ID id) {
        return mappers.mapToLeft(super.getById(id));
    }

    @Override
    public List<E> find(List<ID> ids) {
        return mappers.mapToLeft(super.listByIds(ids));
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
        P po = mappers.mapToRight(entity);
        return super.updateById(po);
    }

    @Override
    public boolean update(List<E> entities) {
        List<P> pos = mappers.mapToRight(entities);
        return super.updateBatchById(pos);
    }

    @Override
    public PageResponse<E> page(PageQuery query) {
        Page<P> page = query.page();
        super.page(page, QueryParser.parse(query));
        return PageResponse.of(page, mappers::mapToLeft);
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
