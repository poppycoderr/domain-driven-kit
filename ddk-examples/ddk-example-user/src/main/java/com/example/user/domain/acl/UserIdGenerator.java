package com.example.user.domain.acl;

import com.example.user.domain.model.entity.UserId;

/**
 * 用户标识生成端口。聚合在写库之前就拥有身份，不依赖数据库自增。
 */
public interface UserIdGenerator {

    UserId nextId();
}
