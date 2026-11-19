package com.example.user.infrastructure.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.user.domain.acl.UserIdGenerator;
import com.example.user.domain.model.entity.UserId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成用户标识，用 DDK MyBatis starter 注册的 {@code IdentifierGenerator}，和 {@code ASSIGN_ID} 主键共用一个发号器。
 * 多实例部署时为每个实例设置不同的 {@code ddk.mybatis.worker-id} 和 {@code datacenter-id}。
 */
@Component
public class SnowflakeUserIdGenerator implements UserIdGenerator {

    private final IdentifierGenerator idGenerator;

    public SnowflakeUserIdGenerator(IdentifierGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public UserId nextId() {
        return UserId.of(idGenerator.nextId(null).longValue());
    }
}
