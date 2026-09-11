package com.ddk.core.domain;

import java.time.Instant;
import java.util.List;

/**
 * 领域模型测试用的最小领域：一个用户聚合，两种标识，一个事件。
 */
final class TestFixtures {

    private TestFixtures() {
    }

    static final class UserId extends Identifier<Long> {
        private UserId(Long value) {
            super(value);
            if (value <= 0) {
                throw new IllegalArgumentException("UserId 必须为正数，实际为 " + value);
            }
        }

        static UserId of(Long value) {
            return new UserId(value);
        }
    }

    /** 和 UserId 同构、同值，用来验证跨类型标识不相等 */
    static final class OrderId extends Identifier<Long> {
        private OrderId(Long value) {
            super(value);
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }

    record Email(String value) implements ValueObject {
        Email {
            if (value == null || !value.contains("@")) {
                throw new IllegalArgumentException("邮箱格式非法：" + value);
            }
        }
    }

    static final class UserRenamedEvent extends AbstractDomainEvent {
        private final String newName;

        UserRenamedEvent(String newName) {
            this.newName = newName;
        }

        UserRenamedEvent(String newName, Instant occurredOn) {
            super(occurredOn);
            this.newName = newName;
        }

        String newName() {
            return newName;
        }
    }

    static final class User extends AggregateRoot<UserId> {
        private String name;
        private boolean active = true;

        User(String name) {
            this.name = name;
        }

        User(UserId id, String name) {
            super(id);
            this.name = name;
        }

        void rename(String newName) {
            this.name = newName;
            registerEvent(new UserRenamedEvent(newName));
        }

        String name() {
            return name;
        }

        boolean active() {
            return active;
        }

        void deactivate() {
            this.active = false;
        }

        /** 模拟基础设施层写入后回填标识与版本 */
        void persistedAs(UserId id, Long version) {
            assignId(id);
            assignVersion(version);
        }

        List<DomainEvent> events() {
            return domainEvents();
        }

        /** 用于验证 registerEvent 的 null 契约 */
        void registerNullEvent() {
            registerEvent(null);
        }
    }

    /** 只做标记的实体，用于验证不同实体类型即使 ID 值相同也不相等 */
    static final class Account extends Entity<UserId> {
        Account(UserId id) {
            super(id);
        }
    }
}
