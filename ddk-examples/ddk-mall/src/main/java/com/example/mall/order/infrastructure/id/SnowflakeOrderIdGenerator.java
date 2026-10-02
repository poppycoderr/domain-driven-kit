package com.example.mall.order.infrastructure.id;

import cn.hutool.core.util.IdUtil;
import com.example.mall.order.domain.acl.OrderIdGenerator;
import com.example.mall.order.domain.model.OrderId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成订单标识。多实例部署时应通过 {@code ddk.mybatis.worker-id} 显式指定机器号。
 */
@Component
public class SnowflakeOrderIdGenerator implements OrderIdGenerator {

    @Override
    public OrderId nextId() {
        return OrderId.of(IdUtil.getSnowflakeNextId());
    }
}
