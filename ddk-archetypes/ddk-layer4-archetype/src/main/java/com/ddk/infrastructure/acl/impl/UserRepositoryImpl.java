package com.ddk.infrastructure.acl.impl;

import com.ddk.core.repository.GenericRepositoryImpl;
import com.ddk.domain.acl.UserRepository;
import com.ddk.domain.model.entity.User;
import com.ddk.infrastructure.orm.mapper.UserMapper;
import com.ddk.infrastructure.orm.po.UserPO;
import org.springframework.stereotype.Repository;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Repository
public class UserRepositoryImpl extends GenericRepositoryImpl<Long, User, UserPO, UserMapper> implements UserRepository {
}
