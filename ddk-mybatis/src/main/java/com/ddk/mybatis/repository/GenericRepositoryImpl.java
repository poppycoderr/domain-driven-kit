package com.ddk.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.mapper.MapperProvider;
import com.ddk.core.mapper.ObjectMapper;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;
import com.ddk.core.repository.GenericRepository;
import com.ddk.mybatis.page.MybatisPlusPageAdapter;
import com.ddk.mybatis.query.QueryParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * 通用仓储实现（基于 MyBatis-Plus）。
 * <p>
 * 子类只要声明四个泛型实参就能获得完整的 CRUD + 分页能力：
 *
 * <pre>{@code
 * @Repository
 * public class UserRepositoryImpl
 *         extends GenericRepositoryImpl<User, Long, UserPO, UserMapper>
 *         implements UserRepository {
 * }
 * }</pre>
 *
 * <h2>它只做两件事</h2>
 * <ol>
 *     <li>在进出口做 Entity ↔ PO 转换，其余委托给 MyBatis-Plus 的 {@code ServiceImpl}</li>
 *     <li>实体是聚合根时，在写入成功后排空并发布它累积的领域事件</li>
 * </ol>
 *
 * <h2>必须显式注册两个方向的映射器</h2>
 * {@code E -> P} 与 {@code P -> E} 各需要一个 {@code @EnhancedMapper}，
 * 否则首次调用时抛 {@code MissingMapperException}——没有静默兜底。
 *
 * <h2>关于自增主键</h2>
 * {@link #create(Object)} 返回的是<b>重新映射出来的新实例</b>，它带有数据库生成的标识；
 * 传入的实例不会被就地修改。想拿到标识必须用返回值。
 * <p>
 * 更推荐给聚合根<b>预生成标识</b>（{@code @TableId(type = IdType.ASSIGN_ID)} 配合
 * {@code ddk-mybatis-starter} 提供的雪花生成器）：这样聚合从诞生起就有身份，
 * 领域事件可以在工厂方法里就带上标识，也不存在「传入实例和返回实例不是同一个」的困扰。
 *
 * @param <E>  领域实体类型
 * @param <ID> 标识类型
 * @param <P>  持久化对象类型
 * @param <M>  MyBatis Mapper 类型
 * @author Elijah Du
 * @date 2025/2/11
 */
@SuppressWarnings("unchecked")
public class GenericRepositoryImpl<E, ID extends Serializable, P, M extends BaseMapper<P>>
        extends ServiceImpl<M, P>
        implements GenericRepository<E, ID> {

    protected final Class<?>[] typeArguments =
            GenericTypeResolver.resolveTypeArguments(this.getClass(), GenericRepositoryImpl.class);
    protected final Class<E> eClass = (Class<E>) typeArguments[0];
    protected final Class<P> pClass = (Class<P>) typeArguments[2];

    @Autowired
    private MapperProvider mapperProvider;

    /**
     * 领域事件发布器是可选依赖：没有引入 {@code ddk-event-starter} 的项目照样能用仓储，
     * 只是不会自动发布事件。
     */
    @Autowired
    private ObjectProvider<DomainEventPublisher> eventPublisher;

    /**
     * 供子类在自定义查询里复用已注册的映射器，不必再注入一次。
     */
    protected MapperProvider mapperProvider() {
        return mapperProvider;
    }

    protected ObjectMapper<E, P> toPo() {
        return mapperProvider.lookup(eClass, pClass);
    }

    protected ObjectMapper<P, E> toEntity() {
        return mapperProvider.lookup(pClass, eClass);
    }

    @Override
    public E create(E entity) {
        P po = toPo().map(entity);
        getBaseMapper().insert(po);
        E saved = toEntity().map(po);
        publishEventsOf(entity);
        return saved;
    }

    @Override
    public List<E> createAll(List<E> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<P> pos = toPo().map(entities);
        super.saveBatch(pos);
        List<E> saved = toEntity().map(pos);
        entities.forEach(this::publishEventsOf);
        return saved;
    }

    @Override
    public E update(E entity) {
        P po = toPo().map(entity);
        super.updateById(po);
        E saved = toEntity().map(po);
        publishEventsOf(entity);
        return saved;
    }

    @Override
    public List<E> updateAll(List<E> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<P> pos = toPo().map(entities);
        super.updateBatchById(pos);
        List<E> saved = toEntity().map(pos);
        entities.forEach(this::publishEventsOf);
        return saved;
    }

    @Override
    public Optional<E> find(ID id) {
        P po = getBaseMapper().selectById(id);
        return Optional.ofNullable(po).map(toEntity()::map);
    }

    @Override
    public List<E> findAll(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return toEntity().map(getBaseMapper().selectByIds(ids));
    }

    @Override
    public boolean remove(ID id) {
        return getBaseMapper().deleteById(id) > 0;
    }

    @Override
    public long removeAll(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        return getBaseMapper().deleteByIds(ids);
    }

    @Override
    public PageResponse<E> page(PageQuery query) {
        Page<P> page = MybatisPlusPageAdapter.toPage(query);
        super.page(page, QueryParser.parse(query));
        return MybatisPlusPageAdapter.toPageResponse(page, toEntity()::map);
    }

    /**
     * 用 {@code SELECT 1 ... LIMIT 1} 判定存在性，而不是把整行查出来再判空——
     * 大字段表上后者的开销完全没有必要。
     */
    @Override
    public boolean existsById(ID id) {
        if (id == null) {
            return false;
        }
        QueryWrapper<P> wrapper = new QueryWrapper<P>().select("1")
                .eq(keyColumn(), id)
                .last(Constants.LIMIT + " 1");
        return getBaseMapper().exists(wrapper);
    }

    @Override
    public long count() {
        return super.count();
    }

    /**
     * 实体是聚合根时，排空并发布它累积的领域事件。
     * <p>
     * 时机是<b>写入成功之后、事务提交之前</b>：此刻只是把事件交给发布器，
     * 真正的投递由订阅方的 {@code @TransactionalEventListener(AFTER_COMMIT)} 推迟到提交后。
     */
    protected void publishEventsOf(E entity) {
        if (entity instanceof AggregateRoot<?> aggregate && aggregate.hasDomainEvents()) {
            DomainEventPublisher publisher = eventPublisher.getIfAvailable();
            if (publisher != null) {
                publisher.publishEventsOf(aggregate);
            }
        }
    }

    /**
     * 从 MyBatis-Plus 的表信息里取主键列名，而不是硬编码 "id"——
     * PO 上的 {@code @TableId} 可能映射到别的列。
     */
    private String keyColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(pClass);
        if (tableInfo == null || tableInfo.getKeyColumn() == null) {
            throw new IllegalStateException(
                    pClass.getName() + " 没有可用的主键信息，请检查它是否标注了 @TableId");
        }
        return tableInfo.getKeyColumn();
    }
}
