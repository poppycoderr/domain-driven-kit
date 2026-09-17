package com.example.user.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.user.domain.model.entity.UserId;

import java.time.Instant;

/**
 * 用户已注册。事件用过去式命名，描述已经发生的事实。
 */
public record UserRegisteredEvent(

        UserId userId,

        String username,

        Instant occurredOn
) implements DomainEvent {

    public UserRegisteredEvent(UserId userId, String username) {
        this(userId, username, Instant.now());
    }
}
