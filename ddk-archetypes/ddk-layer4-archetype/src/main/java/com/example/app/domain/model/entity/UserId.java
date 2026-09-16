package com.example.app.domain.model.entity;

import com.ddk.core.domain.Identifier;

/**
 * 用户标识。
 * <p>
 * 用类型化标识而不是裸 {@code Long}：{@code transfer(userId, accountId)} 传反参数时，
 * 编译器会拦下来。
 *
 * @author Elijah Du
 */
public final class UserId extends Identifier<Long> {

    private UserId(Long value) {
        super(value);
        if (value <= 0) {
            throw new IllegalArgumentException("UserId 必须为正数，实际为 " + value);
        }
    }

    public static UserId of(Long value) {
        return new UserId(value);
    }
}
