package com.ddk.event.starter.internal;

import com.ddk.core.domain.IntegrationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.RoutingTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationEventRoutingTest {

    private final IntegrationEventRouting routing = new IntegrationEventRouting();

    @IntegrationEvent("audit")
    record Audited(
            String who
    ) {
    }

    @IntegrationEvent(value = "audit", key = "missing")
    record Misconfigured(
            String who
    ) {
    }

    @IntegrationEvent(value = "audit", key = "who")
    record Keyed(
            String who
    ) {
    }

    @Test
    void routesWithoutKeyWhenNoneIsDeclared() {
        assertThat(routing.route(new Audited("a"), Audited.class.getAnnotation(IntegrationEvent.class)))
                .isEqualTo(RoutingTarget.forTarget("audit").withoutKey());
    }

    @Test
    void readsTheKeyFromTheNamedAccessor() {
        assertThat(routing.route(new Keyed("alice"), Keyed.class.getAnnotation(IntegrationEvent.class)).getKey()).isEqualTo("alice");
        assertThat(routing.route(new Keyed(null), Keyed.class.getAnnotation(IntegrationEvent.class)))
                .isEqualTo(RoutingTarget.forTarget("audit").withoutKey());
    }

    @Test
    void rejectsAKeyThatIsNotAnAccessor() {
        assertThatThrownBy(() -> routing.route(new Misconfigured("a"), Misconfigured.class.getAnnotation(IntegrationEvent.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key = \"missing\"");
    }
}
