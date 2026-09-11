package com.ddk.infrastructure.acl.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ddk.domain.acl.UserRepository;
import com.ddk.domain.model.entity.User;
import com.ddk.infrastructure.orm.mapper.UserMapper;
import com.ddk.infrastructure.orm.po.UserPO;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户仓储实现。
 * <p>
 * 通用 CRUD、分页、以及「写入后排空并发布领域事件」都由
 * {@link GenericRepositoryImpl} 提供，这里只需要补领域特有的查询。
 * <p>
 * Entity ↔ PO 的转换由 {@code UserPoConverter} / {@code UserEntityConverter}
 * 显式承担——这两个转换器必须存在，否则 {@code MapperProvider} 会直接抛
 * {@code MissingMapperException}，不会静默返回空对象。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Repository
public class UserRepositoryImpl extends GenericRepositoryImpl<User, Long, UserPO, UserMapper>
        implements UserRepository {

    @Override
    public Optional<User> findByUsername(String username) {
        UserPO po = getBaseMapper().selectOne(
                new QueryWrapper<UserPO>().eq("username", username).last("LIMIT 1"));
        return Optional.ofNullable(po).map(toEntity()::map);
    }
}
