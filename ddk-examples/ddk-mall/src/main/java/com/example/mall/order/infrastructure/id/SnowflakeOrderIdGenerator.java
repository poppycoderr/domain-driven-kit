package com.example.mall.order.infrastructure.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.mall.order.domain.acl.OrderIdGenerator;
import com.example.mall.order.domain.model.OrderId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成订单标识，用 DDK MyBatis starter 注册的 {@code IdentifierGenerator}，和 {@code ASSIGN_ID} 主键共用一个发号器。
 * 多实例部署时为每个实例设置不同的 {@code ddk.mybatis.worker-id} 和 {@code datacenter-id}。
 */
@Component
public class SnowflakeOrderIdGenerator implements OrderIdGenerator {

    private final IdentifierGenerator idGenerator;

    public SnowflakeOrderIdGenerator(IdentifierGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public OrderId nextId() {
        return OrderId.of(idGenerator.nextId(null).longValue());
    }
}
