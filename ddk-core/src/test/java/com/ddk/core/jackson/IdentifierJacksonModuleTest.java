package com.ddk.core.jackson;

import com.ddk.core.domain.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("类型化标识的 JSON 读写")
class IdentifierJacksonModuleTest {

    private final JsonMapper mapper = JsonMapper.builder().addModule(new IdentifierJacksonModule()).build();

    static final class OrderId extends Identifier<Long> {

        private OrderId(Long value) {
            super(value);
            if (value <= 0) {
                throw new IllegalArgumentException("OrderId must be positive");
            }
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }

    static final class TicketId extends Identifier<UUID> {

        private TicketId(UUID value) {
            super(value);
        }
    }

    record OrderPlaced(
            OrderId orderId,

            List<TicketId> tickets
    ) {
    }

    @Test
    @DisplayName("写成原始值，读回来仍是同一个类型化标识")
    void roundTripsAsRawValues() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000007");
        OrderPlaced event = new OrderPlaced(OrderId.of(42L), List.of(new TicketId(uuid)));

        String json = mapper.writeValueAsString(event);

        assertThat(json).isEqualTo("{\"orderId\":42,\"tickets\":[\"" + uuid + "\"]}");
        assertThat(mapper.readValue(json, OrderPlaced.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("读取时经过子类自己的取值校验")
    void appliesTheIdentifiersOwnValidation() {
        assertThatThrownBy(() -> mapper.readValue("{\"orderId\":-1,\"tickets\":[]}", OrderPlaced.class))
                .isInstanceOf(MismatchedInputException.class)
                .hasMessageContaining("OrderId must be positive");
    }

    @Test
    @DisplayName("不注册模块时标识会被写成空对象，这正是模块要解决的问题")
    void withoutTheModuleTheValueIsLost() {
        assertThat(JsonMapper.builder().build().writeValueAsString(Map.of("id", OrderId.of(1L)))).isEqualTo("{\"id\":{}}");
    }
}
