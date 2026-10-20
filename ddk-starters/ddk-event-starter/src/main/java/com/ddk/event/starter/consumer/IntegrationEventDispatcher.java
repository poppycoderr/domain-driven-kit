package com.ddk.event.starter.consumer;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import com.ddk.event.starter.internal.EventObservations;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把收到的消息分发给消费方，与消息从哪里来无关。
 * <p>
 * DDK 自带的 RocketMQ 消费者和进程内转发都调用它；接入其他消息中间件时，在自己的监听器里调用 {@link #dispatch} 即可复用
 * 消息体解析、消息头读取、去重，以及链路的接续：发布方写进消息头的链路上下文在这里取出，处理过程接在同一条链路上。
 */
public class IntegrationEventDispatcher {

    public static final String EVENT_ID_HEADER = "ddk-event-id";

    public static final String EVENT_TYPE_HEADER = "ddk-event-type";

    public static final String EVENT_VERSION_HEADER = "ddk-event-version";

    public static final String EVENT_TENANT_HEADER = "ddk-event-tenant";

    /**
     * 在事件租户里处理时的操作者标识，见 {@link IntegrationEventConsumer#runInEventTenant()}。
     */
    public static final String OPERATOR_ID = "ddk-event";

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventDispatcher.class);

    private final List<IntegrationEventConsumer<?>> consumers;

    private final JsonMapper jsonMapper;

    private final @Nullable IdempotentConsumer idempotentConsumer;

    private final EventObservations observations;

    /**
     * @throws IllegalStateException 有消费方要求去重，但没有可用的 {@code IdempotentConsumer}
     */
    public IntegrationEventDispatcher(List<IntegrationEventConsumer<?>> consumers, JsonMapper jsonMapper,
            @Nullable IdempotentConsumer idempotentConsumer) {
        this(consumers, jsonMapper, idempotentConsumer, ObservationRegistry.NOOP);
    }

    /**
     * @param observationRegistry 处理过程的观测登记在这里；应用里有链路追踪时，处理过程接在发布方的链路上
     * @throws IllegalStateException 有消费方要求去重，但没有可用的 {@code IdempotentConsumer}
     */
    public IntegrationEventDispatcher(List<IntegrationEventConsumer<?>> consumers, JsonMapper jsonMapper,
            @Nullable IdempotentConsumer idempotentConsumer, ObservationRegistry observationRegistry) {
        this.observations = new EventObservations(observationRegistry);
        this.consumers = List.copyOf(consumers);
        this.jsonMapper = jsonMapper;
        this.idempotentConsumer = idempotentConsumer;
        Map<String, IntegrationEventConsumer<?>> identities = new HashMap<>();
        for (IntegrationEventConsumer<?> consumer : consumers) {
            if (consumer.source().isBlank() || consumer.group().isBlank()) {
                throw new IllegalStateException(consumer.getClass().getName() + " must declare a group and a source");
            }
            try {
                consumer.contract();
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(consumer.getClass().getName() + " declares an invalid event contract: " + e.getMessage(), e);
            }
            if (!consumer.idempotent()) {
                continue;
            }
            if (idempotentConsumer == null) {
                throw new IllegalStateException(consumer.getClass().getName()
                        + " is idempotent but no IdempotentConsumer is available: set ddk.event.inbox.enabled=true");
            }
            String identity = inboxName(consumer);
            if (consumer.consumerId().isBlank()) {
                throw new IllegalStateException(consumer.getClass().getName()
                        + " is idempotent but has no consumer id (anonymous class?): override consumerId() with a stable name");
            }
            IntegrationEventConsumer<?> clash = identities.putIfAbsent(identity, consumer);
            if (clash != null) {
                throw new IllegalStateException("Idempotent consumers " + clash.getClass().getName() + " and " + consumer.getClass().getName()
                        + " share the identity " + identity + ", so one would skip every event the other handled. "
                        + "Override consumerId() on one of them with a stable name");
            }
        }
    }

    /**
     * 去重表里的消费者名称：消费组加消费方标识。
     */
    static String inboxName(IntegrationEventConsumer<?> consumer) {
        return consumer.group() + ":" + consumer.consumerId();
    }

    public List<IntegrationEventConsumer<?>> consumers() {
        return consumers;
    }

    /**
     * 分发给指定消费组里订阅了这个主题和标签、并且接受这个事件类型的全部消费方。任何一个消费方失败都会抛出异常，由调用方决定是否重投；
     * 重投时已经成功的消费方会再执行一次，这正是处理逻辑必须可重复执行的原因。
     *
     * @return 处理了这条消息的消费方数量，不含因版本不符而跳过的
     * @throws IllegalStateException 消费方的契约不接受这个版本，并且选择了拒绝
     */
    public int dispatch(String group, String topic, @Nullable String tag, Map<String, String> headers, String json) {
        String type = headers.get(EVENT_TYPE_HEADER);
        List<IntegrationEventConsumer<?>> matching = consumers.stream()
                .filter(consumer -> consumer.group().equals(group) && matches(consumer.source(), topic, tag) && acceptsType(consumer, type))
                .toList();
        if (matching.isEmpty()) {
            return 0;
        }
        return observations.consume(group, topic, headers, () -> {
            int handled = 0;
            for (IntegrationEventConsumer<?> consumer : matching) {
                if (invoke(consumer, headers, json)) {
                    handled++;
                }
            }
            return handled;
        });
    }

    private static boolean acceptsType(IntegrationEventConsumer<?> consumer, @Nullable String type) {
        EventContract contract = consumer.contract();
        return contract == null || contract.type().equals(type);
    }

    /**
     * 版本检查在解析消息体之前：不认识的版本不能按消费方的类型解析。
     */
    private <T> boolean invoke(IntegrationEventConsumer<T> consumer, Map<String, String> headers, String json) {
        String eventId = headers.get(EVENT_ID_HEADER);
        EventContract contract = consumer.contract();
        if (contract != null && !contract.versions().contains(version(headers))) {
            String version = headers.get(EVENT_VERSION_HEADER);
            String problem = consumer.getClass().getName() + " accepts " + contract.type() + " versions " + contract.versions() + ", got "
                    + (version == null ? "a message without " + EVENT_VERSION_HEADER : "version " + version);
            if (contract.otherVersions() == EventContract.OtherVersions.REJECT) {
                throw new IllegalStateException(problem);
            }
            log.warn("Skipped event [{}]: {}", eventId, problem);
            return false;
        }
        if (!consumer.runInEventTenant()) {
            return decodeAndHandle(consumer, headers, json);
        }
        String tenant = headers.get(EVENT_TENANT_HEADER);
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException(consumer.getClass().getName() + " runs in the event's tenant but the message has no "
                    + EVENT_TENANT_HEADER + " header: declare @IntegrationEvent(tenant = ...) on the published event");
        }
        return OperatorContext.callAs(new Operator(OPERATOR_ID, null, tenant), () -> decodeAndHandle(consumer, headers, json));
    }

    private <T> boolean decodeAndHandle(IntegrationEventConsumer<T> consumer, Map<String, String> headers, String json) {
        String eventId = headers.get(EVENT_ID_HEADER);
        ReceivedEvent<T> event = new ReceivedEvent<>(eventId, headers.get(EVENT_TYPE_HEADER), version(headers),
                jsonMapper.readValue(json, consumer.payloadType()));
        if (!consumer.idempotent() || idempotentConsumer == null) {
            consumer.handle(event);
            return true;
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalStateException(consumer.getClass().getName() + " is idempotent but the message has no " + EVENT_ID_HEADER
                    + " header: declare @IntegrationEvent(id = ...) on the published event");
        }
        String identity = inboxName(consumer);
        boolean processed = idempotentConsumer.handle(identity, eventId, () -> consumer.handle(event));
        if (!processed) {
            log.debug("Skipped duplicate event [{}] for {}", eventId, identity);
        }
        return true;
    }

    static boolean matches(String source, String topic, @Nullable String tag) {
        int separator = source.indexOf(':');
        if (separator < 0) {
            return source.equals(topic);
        }
        return source.substring(0, separator).equals(topic) && source.substring(separator + 1).equals(tag);
    }

    private static int version(Map<String, String> headers) {
        String version = headers.get(EVENT_VERSION_HEADER);
        try {
            return version == null ? 0 : Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
