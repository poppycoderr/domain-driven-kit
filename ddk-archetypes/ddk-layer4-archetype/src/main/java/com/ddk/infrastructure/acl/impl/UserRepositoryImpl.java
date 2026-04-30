package com.ddk.infrastructure.acl.impl;

import com.ddk.domain.acl.UserRepository;
import com.ddk.domain.model.entity.User;
import com.ddk.infrastructure.orm.mapper.UserMapper;
import com.ddk.infrastructure.orm.po.UserPO;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import org.springframework.stereotype.Repository;

/**
 * 用户仓储实现
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Repository
public class UserRepositoryImpl extends GenericRepositoryImpl<Long, User, UserPO, UserMapper> implements UserRepository {
}
