package com.ddk.infrastructure.id;

import cn.hutool.core.util.IdUtil;
import com.ddk.domain.acl.UserIdGenerator;
import com.ddk.domain.model.entity.UserId;
import org.springframework.stereotype.Component;

/**
 * 用雪花算法实现标识生成。
 * <p>
 * 多实例部署时要显式指定机器号，否则 Hutool 按本机 MAC / IP 推导，
 * 在容器环境下并不可靠——具体配置见 {@code ddk.mybatis.worker-id}。
 *
 * @author Elijah Du
 */
@Component
public class SnowflakeUserIdGenerator implements UserIdGenerator {

    @Override
    public UserId nextId() {
        return UserId.of(IdUtil.getSnowflakeNextId());
    }
}
