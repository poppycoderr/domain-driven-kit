package com.ddk.event.starter.internal;

import com.ddk.core.domain.IntegrationEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationEventContractVerifierTest {

    private final IntegrationEventRouting routing = new IntegrationEventRouting();

    private IntegrationEventContractVerifier verifierFor(String basePackage) {
        return new IntegrationEventContractVerifier(List.of(basePackage), routing, getClass().getClassLoader());
    }

    @IntegrationEvent(value = "orders", version = 0)
    record Unversioned(
            String orderId
    ) {
    }

    @IntegrationEvent(value = "orders", type = "OrderClosed", version = 2)
    record OrderClosedV2(
            String orderId
    ) {
    }

    @Test
    void aKeyThatIsNotAnAccessorFailsAtStartup() {
        assertThatThrownBy(() -> verifierFor("com.ddk.event.starter.fixture.badkey").afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key = \"orderNo\"")
                .hasMessageContaining("OrderShipped");
    }

    @Test
    void twoClassesCannotShareOneContract() {
        assertThatThrownBy(() -> verifierFor("com.ddk.event.starter.fixture.duplicate").afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OrderClosed v1")
                .hasMessageContaining("OrderCancelled");
    }

    @Test
    void versionsStartAtOne() {
        assertThatThrownBy(() -> verifierFor("x").verify(Unversioned.class, new HashMap<>()))
                .hasMessageContaining("version >= 1");
    }

    @Test
    void aNewVersionOfTheSameTypeIsAllowed() {
        HashMap<String, String> contracts = new HashMap<>();
        contracts.put("OrderClosed v1", "com.acme.OrderClosed");

        assertThatCode(() -> verifierFor("x").verify(OrderClosedV2.class, contracts)).doesNotThrowAnyException();
    }
}
