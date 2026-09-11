package com.ddk.domain.event;

import com.ddk.core.domain.AbstractDomainEvent;
import com.ddk.domain.model.entity.UserId;

/**
 * 用户已被禁用。
 * <p>
 * 继承 {@code AbstractDomainEvent} 的写法：省掉 occurredOn 的样板代码，
 * 代价是不能用 record。两种写法按需选。
 *
 * @author Elijah Du
 */
public class UserDisabledEvent extends AbstractDomainEvent {

    private final UserId userId;
    private final String reason;

    public UserDisabledEvent(UserId userId, String reason) {
        this.userId = userId;
        this.reason = reason;
    }

    public UserId userId() {
        return userId;
    }

    public String reason() {
        return reason;
    }
}
