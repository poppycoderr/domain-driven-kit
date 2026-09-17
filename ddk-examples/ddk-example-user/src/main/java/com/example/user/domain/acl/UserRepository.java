package com.example.user.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.user.domain.model.entity.User;

import java.util.Optional;

/**
 * 用户仓储契约。定义在领域层、实现在基础设施层：领域只声明需要什么，不关心存储技术。
 */
public interface UserRepository extends GenericRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
