package com.example.mall.inventory.infrastructure.id;

import cn.hutool.core.util.IdUtil;
import com.example.mall.inventory.domain.acl.ReservationIdGenerator;
import com.example.mall.inventory.domain.model.ReservationId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成预占记录标识。
 */
@Component
public class SnowflakeReservationIdGenerator implements ReservationIdGenerator {

    @Override
    public ReservationId nextId() {
        return ReservationId.of(IdUtil.getSnowflakeNextId());
    }
}
