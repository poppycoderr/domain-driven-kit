package com.ddk.event.starter.consumer;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("集成事件分发")
class IntegrationEventDispatcherTest {

    private static final String JSON = "{\"orderId\":42,\"somethingNew\":true}";

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    record OrderPaid(
            Long orderId
    ) {
    }

    static class Recording implements IntegrationEventConsumer<OrderPaid> {

        final List<ReceivedEvent<OrderPaid>> received = new ArrayList<>();

        private final String group;

        private final String source;

        private final boolean idempotent;

        Recording(String group, String source, boolean idempotent) {
            this.group = group;
            this.source = source;
            this.idempotent = idempotent;
        }

        @Override
        public String group() {
            return group;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public Class<OrderPaid> payloadType() {
            return OrderPaid.class;
        }

        @Override
        public void handle(ReceivedEvent<OrderPaid> event) {
            received.add(event);
        }

        @Override
        public boolean idempotent() {
            return idempotent;
        }
    }

    @Test
    @DisplayName("按消费组、主题、标签匹配；只写主题的消费方接收全部标签")
    void matchesByGroupTopicAndTag() {
        Recording paid = new Recording("billing", "orders:paid", false);
        Recording everything = new Recording("billing", "orders", false);
        Recording otherGroup = new Recording("shipping", "orders:paid", false);
        Recording otherTag = new Recording("billing", "orders:cancelled", false);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(paid, everything, otherGroup, otherTag), jsonMapper, null);

        int handled = dispatcher.dispatch("billing", "orders", "paid", Map.of(), JSON);

        assertThat(handled).isEqualTo(2);
        assertThat(paid.received).hasSize(1);
        assertThat(everything.received).hasSize(1);
        assertThat(otherGroup.received).isEmpty();
        assertThat(otherTag.received).isEmpty();
        assertThat(dispatcher.dispatch("billing", "refunds", null, Map.of(), JSON)).isZero();
    }

    @Test
    @DisplayName("消息体按消费方的类型解析，契约头进入 ReceivedEvent")
    void decodesThePayloadAndHeaders() {
        Recording consumer = new Recording("billing", "orders:paid", false);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(consumer), jsonMapper, null);

        dispatcher.dispatch("billing", "orders", "paid",
                Map.of("ddk-event-id", "evt-1", "ddk-event-type", "order.paid", "ddk-event-version", "2"), JSON);
        dispatcher.dispatch("billing", "orders", "paid", Map.of("ddk-event-version", "not-a-number"), JSON);

        assertThat(consumer.received.get(0)).isEqualTo(new ReceivedEvent<>("evt-1", "order.paid", 2, new OrderPaid(42L)));
        assertThat(consumer.received.get(1)).isEqualTo(new ReceivedEvent<>(null, null, 0, new OrderPaid(42L)));
    }

    @Test
    @DisplayName("要求去重的消费方经 IdempotentConsumer 执行，重复的事件不再处理")
    void idempotentConsumersGoThroughTheInbox() {
        Recording consumer = new Recording("billing", "orders:paid", true);
        IdempotentConsumer inbox = mock(IdempotentConsumer.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(inbox).handle(eq("billing:Recording"), eq("evt-1"), any());
        when(inbox.handle(eq("billing:Recording"), eq("evt-2"), any())).thenReturn(false);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(consumer), jsonMapper, inbox);

        dispatcher.dispatch("billing", "orders", "paid", Map.of("ddk-event-id", "evt-1"), JSON);
        dispatcher.dispatch("billing", "orders", "paid", Map.of("ddk-event-id", "evt-2"), JSON);

        assertThat(consumer.received).extracting(ReceivedEvent::eventId).containsExactly("evt-1");
        assertThatThrownBy(() -> dispatcher.dispatch("billing", "orders", "paid", Map.of(), JSON))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no ddk-event-id header");
    }

    @Test
    @DisplayName("声明有误时在创建分发器时就报错")
    void invalidDeclarationsFailEarly() {
        assertThatThrownBy(() -> new IntegrationEventDispatcher(List.of(new Recording("billing", "orders", true)), jsonMapper, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("set ddk.event.inbox.enabled=true");
        assertThatThrownBy(() -> new IntegrationEventDispatcher(List.of(new Recording("billing", " ", false)), jsonMapper, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must declare a group and a source");
    }

    @Test
    @DisplayName("消费方抛出的异常原样传给调用方，由它决定重投")
    void failuresPropagate() {
        IntegrationEventConsumer<OrderPaid> failing = new Recording("billing", "orders", false) {

            @Override
            public void handle(ReceivedEvent<OrderPaid> event) {
                throw new IllegalStateException("database down");
            }
        };
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(failing), jsonMapper, null);

        assertThatThrownBy(() -> dispatcher.dispatch("billing", "orders", null, Map.of(), JSON)).hasMessage("database down");
    }

    private static Recording withContract(EventContract contract) {
        return new Recording("billing", "orders", false) {

            @Override
            public EventContract contract() {
                return contract;
            }
        };
    }

    private static Map<String, String> headers(String type, String version) {
        return Map.of(IntegrationEventDispatcher.EVENT_TYPE_HEADER, type, IntegrationEventDispatcher.EVENT_VERSION_HEADER, version);
    }

    @Test
    @DisplayName("声明了契约的消费方只接收这个类型；没声明的照常接收全部")
    void contractsFilterByType() {
        Recording paidOnly = withContract(EventContract.of("OrderPaid", 1));
        Recording everything = new Recording("billing", "orders", false);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(paidOnly, everything), jsonMapper, null);

        assertThat(dispatcher.dispatch("billing", "orders", null, headers("OrderShipped", "1"), JSON)).isEqualTo(1);
        assertThat(dispatcher.dispatch("billing", "orders", null, Map.of(), JSON)).isEqualTo(1);
        assertThat(paidOnly.received).isEmpty();

        assertThat(dispatcher.dispatch("billing", "orders", null, headers("OrderPaid", "1"), JSON)).isEqualTo(2);
        assertThat(paidOnly.received).singleElement().extracting(ReceivedEvent::version).isEqualTo(1);
    }

    @Test
    @DisplayName("版本不在契约里时默认跳过，并且不解析消息体；并行发布的新版本不会被当成旧版本处理")
    void otherVersionsAreSkippedBeforeDecoding() {
        Recording v1 = withContract(EventContract.of("OrderPaid", 1));
        Recording v1AndV2 = withContract(EventContract.of("OrderPaid", 1, 2));
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(v1, v1AndV2), jsonMapper, null);

        assertThat(dispatcher.dispatch("billing", "orders", null, headers("OrderPaid", "2"), JSON)).isEqualTo(1);
        assertThat(v1.received).isEmpty();
        assertThat(v1AndV2.received).singleElement().extracting(ReceivedEvent::version).isEqualTo(2);

        IntegrationEventDispatcher onlyV1 = new IntegrationEventDispatcher(List.of(v1), jsonMapper, null);
        assertThat(onlyV1.dispatch("billing", "orders", null, headers("OrderPaid", "3"), "not json")).isZero();
        assertThat(onlyV1.dispatch("billing", "orders", null, headers("OrderPaid", "one"), JSON)).isZero();
        assertThat(onlyV1.dispatch("billing", "orders", null, Map.of(IntegrationEventDispatcher.EVENT_TYPE_HEADER, "OrderPaid"), JSON)).isZero();
        assertThat(v1.received).isEmpty();
    }

    @Test
    @DisplayName("跳过只在每个消费方的每个版本第一次出现时警告，之后记 DEBUG")
    void skipsAreWarnedOncePerConsumerAndVersion() {
        Recording v1 = withContract(EventContract.of("OrderPaid", 1));
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(v1), jsonMapper, null);
        Logger logger = (Logger) LoggerFactory.getLogger(IntegrationEventDispatcher.class);
        Level original = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            for (int i = 0; i < 3; i++) {
                dispatcher.dispatch("billing", "orders", null, headers("OrderPaid", "2"), JSON);
            }
            dispatcher.dispatch("billing", "orders", null, headers("OrderPaid", "3"), JSON);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(original);
        }

        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.DEBUG, Level.DEBUG, Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("version 2", "logged at DEBUG");
    }

    @Test
    @DisplayName("选择拒绝时，版本不在契约里的消息抛出异常，由消息中间件重投")
    void otherVersionsCanBeRejected() {
        Recording strict = withContract(EventContract.of("OrderPaid", 1).rejectOtherVersions());
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(strict), jsonMapper, null);

        assertThatThrownBy(() -> dispatcher.dispatch("billing", "orders", null, headers("OrderPaid", "2"), JSON))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accepts OrderPaid versions [1], got version 2");
        assertThatThrownBy(() -> dispatcher.dispatch("billing", "orders", null, Map.of(IntegrationEventDispatcher.EVENT_TYPE_HEADER, "OrderPaid"), JSON))
                .hasMessageContaining("got a message without ddk-event-version");
        assertThat(strict.received).isEmpty();

        assertThat(dispatcher.dispatch("billing", "orders", null, headers("OrderPaid", "1"), JSON)).isEqualTo(1);
    }

    @Test
    @DisplayName("契约写错时在创建分发器时就报错")
    void invalidContractsFailEarly() {
        assertThatThrownBy(() -> EventContract.of("OrderPaid")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("versions of 1 or above");
        assertThatThrownBy(() -> EventContract.of("OrderPaid", 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventContract.of(" ", 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs a type");

        Recording broken = new Recording("billing", "orders", false) {

            @Override
            public EventContract contract() {
                return EventContract.of("OrderPaid");
            }
        };
        assertThatThrownBy(() -> new IntegrationEventDispatcher(List.of(broken), jsonMapper, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares an invalid event contract");
    }

    static class TenantRecording extends Recording {

        final List<String> operators = new ArrayList<>();

        private final boolean inEventTenant;

        TenantRecording(boolean idempotent, boolean inEventTenant) {
            super("billing", "orders", idempotent);
            this.inEventTenant = inEventTenant;
        }

        @Override
        public void handle(ReceivedEvent<OrderPaid> event) {
            super.handle(event);
            operators.add(OperatorContext.current().map(operator -> operator.id() + "/" + operator.tenantId()).orElse("none"));
        }

        @Override
        public boolean runInEventTenant() {
            return inEventTenant;
        }
    }

    @Test
    @DisplayName("选择在事件租户里处理时，以系统操作者加消息里的租户执行，结束后恢复调用方原来的上下文")
    void consumersCanRunInTheEventTenant() {
        TenantRecording consumer = new TenantRecording(false, true);
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(consumer), jsonMapper, null);
        Operator listener = Operator.of("listener");

        OperatorContext.runAs(listener, () -> {
            dispatcher.dispatch("billing", "orders", null, Map.of(IntegrationEventDispatcher.EVENT_TENANT_HEADER, "7"), JSON);
            assertThat(OperatorContext.current()).contains(listener);
        });
        dispatcher.dispatch("billing", "orders", null, Map.of(IntegrationEventDispatcher.EVENT_TENANT_HEADER, "8"), JSON);

        assertThat(consumer.operators).containsExactly("ddk-event/7", "ddk-event/8");
        assertThat(OperatorContext.current()).isEmpty();
    }

    @Test
    @DisplayName("消息没有租户时处理失败，不在没有租户的情况下执行；没选择的消费方不读取消息里的租户")
    void aMissingTenantFailsAndOthersIgnoreIt() {
        TenantRecording inTenant = new TenantRecording(false, true);
        IntegrationEventDispatcher strict = new IntegrationEventDispatcher(List.of(inTenant), jsonMapper, null);
        assertThatThrownBy(() -> strict.dispatch("billing", "orders", null, Map.of(), JSON))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no ddk-event-tenant header");
        assertThatThrownBy(() -> strict.dispatch("billing", "orders", null, Map.of(IntegrationEventDispatcher.EVENT_TENANT_HEADER, " "), JSON))
                .isInstanceOf(IllegalStateException.class);
        assertThat(inTenant.received).isEmpty();

        TenantRecording plain = new TenantRecording(false, false);
        new IntegrationEventDispatcher(List.of(plain), jsonMapper, null)
                .dispatch("billing", "orders", null, Map.of(IntegrationEventDispatcher.EVENT_TENANT_HEADER, "7"), JSON);
        assertThat(plain.operators).containsExactly("none");
    }

    @Test
    @DisplayName("去重登记也在事件租户里进行")
    void theInboxRunsInTheEventTenant() {
        TenantRecording consumer = new TenantRecording(true, true);
        IdempotentConsumer inbox = mock(IdempotentConsumer.class);
        List<String> tenantsAtRegistration = new ArrayList<>();
        doAnswer(invocation -> {
            tenantsAtRegistration.add(OperatorContext.required().tenantId());
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(inbox).handle(eq("billing:TenantRecording"), eq("evt-1"), any());
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(consumer), jsonMapper, inbox);

        dispatcher.dispatch("billing", "orders", null,
                Map.of(IntegrationEventDispatcher.EVENT_ID_HEADER, "evt-1", IntegrationEventDispatcher.EVENT_TENANT_HEADER, "7"), JSON);

        assertThat(tenantsAtRegistration).containsExactly("7");
        assertThat(consumer.operators).containsExactly("ddk-event/7");
    }
}
