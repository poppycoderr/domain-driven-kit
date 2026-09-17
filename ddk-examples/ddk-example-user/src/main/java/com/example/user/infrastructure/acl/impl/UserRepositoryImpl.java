package com.example.user.infrastructure.acl.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import com.example.user.domain.acl.UserRepository;
import com.example.user.domain.model.entity.User;
import com.example.user.infrastructure.orm.mapper.UserMapper;
import com.example.user.infrastructure.orm.po.UserPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户仓储实现。通用 CRUD、分页、乐观锁与领域事件发布由 {@link GenericRepositoryImpl} 提供，这里只补领域特有的查询。
 */
@Repository
public class UserRepositoryImpl extends GenericRepositoryImpl<User, Long, UserPO, UserMapper> implements UserRepository {

    @Override
    public Optional<User> findByUsername(String username) {
        UserPO po = getBaseMapper().selectOne(Wrappers.lambdaQuery(UserPO.class).eq(UserPO::getUsername, username));
        return Optional.ofNullable(po).map(toEntity()::map);
    }
}
