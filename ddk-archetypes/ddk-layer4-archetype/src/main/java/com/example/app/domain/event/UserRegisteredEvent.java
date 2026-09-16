package com.example.app.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.app.domain.model.entity.UserId;

import java.time.Instant;

/**
 * 用户已注册。
 * <p>
 * 事件命名用过去式——它描述的是已经发生的事实，不是要执行的命令。
 * 用 record 写法：字段显式、天然不可变。
 *
 * @author Elijah Du
 */
public record UserRegisteredEvent(UserId userId, String username, Instant occurredOn) implements DomainEvent {

    public UserRegisteredEvent(UserId userId, String username) {
        this(userId, username, Instant.now());
    }
}
