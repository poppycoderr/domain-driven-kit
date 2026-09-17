package com.example.user.infrastructure.id;

import cn.hutool.core.util.IdUtil;
import com.example.user.domain.acl.UserIdGenerator;
import com.example.user.domain.model.entity.UserId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成用户标识。多实例部署时应通过 {@code ddk.mybatis.worker-id} 显式指定机器号。
 */
@Component
public class SnowflakeUserIdGenerator implements UserIdGenerator {

    @Override
    public UserId nextId() {
        return UserId.of(IdUtil.getSnowflakeNextId());
    }
}
