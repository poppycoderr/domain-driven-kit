package io.github.ddk.infrastructure.acl.impl;

import io.github.ddk.core.repository.GenericRepositoryImpl;
import io.github.ddk.domain.acl.UserRepository;
import io.github.ddk.domain.model.entity.User;
import io.github.ddk.infrastructure.orm.mapper.UserMapper;
import io.github.ddk.infrastructure.orm.po.UserPO;
import org.springframework.stereotype.Repository;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Repository
public class UserRepositoryImpl extends GenericRepositoryImpl<Long, User, UserPO, UserMapper> implements UserRepository {
}
