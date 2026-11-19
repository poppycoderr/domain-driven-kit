package com.example.mall.inventory.infrastructure.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.mall.inventory.domain.acl.ReservationIdGenerator;
import com.example.mall.inventory.domain.model.ReservationId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成预占记录标识，用 DDK MyBatis starter 注册的 {@code IdentifierGenerator}，和 {@code ASSIGN_ID} 主键共用一个发号器。
 * 多实例部署时为每个实例设置不同的 {@code ddk.mybatis.worker-id} 和 {@code datacenter-id}。
 */
@Component
public class SnowflakeReservationIdGenerator implements ReservationIdGenerator {

    private final IdentifierGenerator idGenerator;

    public SnowflakeReservationIdGenerator(IdentifierGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public ReservationId nextId() {
        return ReservationId.of(idGenerator.nextId(null).longValue());
    }
}
