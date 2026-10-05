package com.example.mall.inventory.domain.acl;

import com.example.mall.inventory.domain.model.ReservationId;

/**
 * 预占记录标识生成器。
 */
public interface ReservationIdGenerator {

    ReservationId nextId();
}
