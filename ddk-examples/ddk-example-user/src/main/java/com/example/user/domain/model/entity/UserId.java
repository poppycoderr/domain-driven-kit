package com.example.user.domain.model.entity;

import com.ddk.core.domain.Identifier;

/**
 * 用户标识。类型化标识让「把订单 ID 当用户 ID 传」在编译期就报错。
 */
public final class UserId extends Identifier<Long> {

    private UserId(Long value) {
        super(value);
        if (value <= 0) {
            throw new IllegalArgumentException("UserId must be positive, got " + value);
        }
    }

    public static UserId of(Long value) {
        return new UserId(value);
    }
}
