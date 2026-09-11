package com.ddk.core.domain;

import com.ddk.core.domain.TestFixtures.User;
import com.ddk.core.domain.TestFixtures.UserId;
import com.ddk.core.domain.TestFixtures.UserRenamedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AggregateRoot：事件收集与版本号")
class AggregateRootTest {

    @Test
    @DisplayName("领域行为登记事件，按发生顺序保存")
    void registersEventsInOrder() {
        User user = new User(UserId.of(1L), "alice");
        user.rename("bob");
        user.rename("carol");

        List<DomainEvent> events = user.events();
        assertEquals(2, events.size());
        assertEquals("bob", ((UserRenamedEvent) events.get(0)).newName());
        assertEquals("carol", ((UserRenamedEvent) events.get(1)).newName());
    }

    @Test
    @DisplayName("新建的聚合没有待发布事件")
    void startsWithNoEvents() {
        User user = new User("alice");
        assertFalse(user.hasDomainEvents());
        assertTrue(user.events().isEmpty());
    }

    @Test
    @DisplayName("domainEvents() 返回不可变视图，外部改不动")
    void exposesUnmodifiableView() {
        User user = new User(UserId.of(1L), "alice");
        user.rename("bob");

        List<DomainEvent> view = user.events();
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> view.add(new UserRenamedEvent("mallory")));
    }

    @Test
    @DisplayName("drain 取出快照并清空，快照不受后续变更影响")
    void drainReturnsSnapshotAndClears() {
        User user = new User(UserId.of(1L), "alice");
        user.rename("bob");

        List<DomainEvent> drained = user.drainDomainEvents();

        assertEquals(1, drained.size());
        assertFalse(user.hasDomainEvents(), "drain 之后聚合上不应再有待发布事件");

        user.rename("carol");
        assertEquals(1, drained.size(), "已取出的快照不应被后续登记影响");
        assertEquals(1, user.events().size());
    }

    @Test
    @DisplayName("clearEvents 清空待发布事件")
    void clearEventsEmptiesList() {
        User user = new User(UserId.of(1L), "alice");
        user.rename("bob");

        user.clearEvents();

        assertFalse(user.hasDomainEvents());
    }

    @Test
    @DisplayName("拒绝登记 null 事件")
    void rejectsNullEvent() {
        User user = new User(UserId.of(1L), "alice");
        assertThrows(NullPointerException.class, user::registerNullEvent);
        assertFalse(user.hasDomainEvents());
    }

    @Test
    @DisplayName("版本号由基础设施层回填，默认为 null")
    void versionIsAssignedByInfrastructure() {
        User user = new User("alice");
        assertNull(user.version());

        user.persistedAs(UserId.of(1L), 3L);
        assertEquals(3L, user.version());
    }
}
