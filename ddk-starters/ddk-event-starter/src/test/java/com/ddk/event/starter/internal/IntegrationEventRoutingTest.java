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

    @IntegrationEvent(value = "audit", id = "eventId", tenant = "tenantId")
    record TenantScoped(
            String eventId,

            Long tenantId
    ) {
    }

    @IntegrationEvent(value = "audit", tenant = "owner")
    record WrongTenant(
            String who
    ) {
    }

    @Test
    void writesTheTenantFromTheNamedAccessor() {
        assertThat(routing.headers(new TenantScoped("e-1", 7L))).containsEntry(IntegrationEventRouting.EVENT_TENANT_HEADER, "7")
                .containsEntry(IntegrationEventRouting.EVENT_ID_HEADER, "e-1");
        assertThat(routing.headers(new TenantScoped("e-2", null))).doesNotContainKey(IntegrationEventRouting.EVENT_TENANT_HEADER);
        assertThat(routing.headers(new Audited("a"))).doesNotContainKey(IntegrationEventRouting.EVENT_TENANT_HEADER);
    }

    @Test
    void rejectsATenantThatIsNotAnAccessor() {
        assertThatThrownBy(() -> routing.validate(WrongTenant.class, WrongTenant.class.getAnnotation(IntegrationEvent.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant = \"owner\"");
    }
}
