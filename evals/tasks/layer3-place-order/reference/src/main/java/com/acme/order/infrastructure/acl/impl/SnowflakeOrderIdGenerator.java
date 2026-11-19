package com.acme.order.infrastructure.acl.impl;

import com.acme.order.business.acl.OrderIdGenerator;
import com.acme.order.business.model.OrderId;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用 MyBatis starter 注册的雪花生成器取号，遵循 ddk.mybatis.worker-id 与 datacenter-id。
 */
@Component
@RequiredArgsConstructor
public class SnowflakeOrderIdGenerator implements OrderIdGenerator {

    private final IdentifierGenerator idGenerator;

    @Override
    public OrderId nextId() {
        return OrderId.of(idGenerator.nextId(null).longValue());
    }
}
