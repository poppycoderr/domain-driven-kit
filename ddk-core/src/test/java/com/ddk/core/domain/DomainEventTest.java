package com.ddk.core.domain;

import com.ddk.core.domain.TestFixtures.UserRenamedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DomainEvent 与 DomainEventPublisher")
class DomainEventTest {

    @Test
    @DisplayName("occurredOn 在构造时固定，表示事实发生的时刻")
    void occurredOnFixedAtConstruction() throws InterruptedException {
        Instant before = Instant.now();
        UserRenamedEvent event = new UserRenamedEvent("bob");
        Thread.sleep(5);
        Instant after = Instant.now();

        assertFalse(event.occurredOn().isBefore(before));
        assertTrue(event.occurredOn().isBefore(after));
        assertEquals(event.occurredOn(), event.occurredOn(), "多次读取应当稳定");
    }

    @Test
    @DisplayName("支持指定历史时刻，用于事件重放与补偿")
    void acceptsExplicitInstant() {
        Instant past = Instant.parse("2020-01-01T00:00:00Z");
        assertEquals(past, new UserRenamedEvent("bob", past).occurredOn());
    }

    @Test
    @DisplayName("拒绝 null 时刻")
    void rejectsNullInstant() {
        assertThrows(IllegalArgumentException.class, () -> new UserRenamedEvent("bob", null));
    }

    @Test
    @DisplayName("eventType 默认取简单类名，可用于日志与消息路由")
    void defaultEventType() {
        assertEquals("UserRenamedEvent", new UserRenamedEvent("bob").eventType());
    }

    @Test
    @DisplayName("publishAll 逐个发布，null 集合安全")
    void publishAllDelegates() {
        List<DomainEvent> published = new ArrayList<>();
        DomainEventPublisher publisher = published::add;

        publisher.publishAll(List.of(new UserRenamedEvent("a"), new UserRenamedEvent("b")));
        assertEquals(2, published.size());

        assertDoesNotThrow(() -> publisher.publishAll(null));
        assertEquals(2, published.size());
    }
}
