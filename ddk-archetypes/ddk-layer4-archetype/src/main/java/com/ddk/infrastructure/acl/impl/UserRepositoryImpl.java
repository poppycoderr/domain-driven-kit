package com.ddk.infrastructure.acl.impl;

import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.domain.acl.UserRepository;
import com.ddk.domain.model.entity.User;
import com.ddk.domain.model.entity.UserId;
import com.ddk.infrastructure.orm.mapper.UserMapper;
import com.ddk.infrastructure.orm.po.UserPO;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 用户仓储实现。
 * <p>
 * 通用 CRUD 由 {@link GenericRepositoryImpl} 提供，Entity↔PO 的转换由
 * {@code UserPoConverter} / {@code UserEntityConverter} 显式承担——
 * 这两个转换器必须存在，否则 {@code MapperProvider} 会直接抛
 * {@code MissingMapperException}，不会静默返回空对象。
 * <p>
 * 领域事件的排空放在这里：<b>保存成功之后、事务提交之前</b>。
 * 事件此时只是交给 Spring，真正的投递由订阅方的
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 推迟到提交后。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Repository
public class UserRepositoryImpl extends GenericRepositoryImpl<User, Long, UserPO, UserMapper>
        implements UserRepository {

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Override
    public User save(User user) {
        UserPO po = mapperProvider().lookup(User.class, UserPO.class).map(user);
        getBaseMapper().insert(po);
        user.onPersisted(UserId.of(po.getId()));
        publishEventsOf(user);
        return user;
    }

    @Override
    public User saveChanges(User user) {
        update(user);
        publishEventsOf(user);
        return user;
    }

    /**
     * 先取出快照再发布：{@code drainDomainEvents()} 是「复制 + 清空」的原子操作，
     * 避免发布过程中新登记的事件被误清。
     */
    private void publishEventsOf(User user) {
        if (user.hasDomainEvents()) {
            eventPublisher.publishAll(user.drainDomainEvents());
        }
    }
}
