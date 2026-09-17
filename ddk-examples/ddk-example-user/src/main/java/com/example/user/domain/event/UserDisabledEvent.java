package com.example.user.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.user.domain.model.entity.UserId;

import java.time.Instant;

/**
 * 用户已被禁用。
 */
public record UserDisabledEvent(

        UserId userId,

        String reason,

        Instant occurredOn
) implements DomainEvent {

    public UserDisabledEvent(UserId userId, String reason) {
        this(userId, reason, Instant.now());
    }
}
