package com.ddk.event.starter.consumer;

import com.ddk.event.starter.inbox.IdempotentConsumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 把收到的消息分发给消费方，与消息从哪里来无关。
 * <p>
 * DDK 自带的 RocketMQ 消费者和进程内转发都调用它；接入其他消息中间件时，在自己的监听器里调用 {@link #dispatch} 即可复用
 * 消息体解析、消息头读取和去重。
 */
public class IntegrationEventDispatcher {

    public static final String EVENT_ID_HEADER = "ddk-event-id";

    public static final String EVENT_TYPE_HEADER = "ddk-event-type";

    public static final String EVENT_VERSION_HEADER = "ddk-event-version";

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventDispatcher.class);

    private final List<IntegrationEventConsumer<?>> consumers;

    private final JsonMapper jsonMapper;

    private final @Nullable IdempotentConsumer idempotentConsumer;

    /**
     * @throws IllegalStateException 有消费方要求去重，但没有可用的 {@code IdempotentConsumer}
     */
    public IntegrationEventDispatcher(List<IntegrationEventConsumer<?>> consumers, JsonMapper jsonMapper,
            @Nullable IdempotentConsumer idempotentConsumer) {
        this.consumers = List.copyOf(consumers);
        this.jsonMapper = jsonMapper;
        this.idempotentConsumer = idempotentConsumer;
        for (IntegrationEventConsumer<?> consumer : consumers) {
            if (consumer.source().isBlank() || consumer.group().isBlank()) {
                throw new IllegalStateException(consumer.getClass().getName() + " must declare a group and a source");
            }
            if (consumer.idempotent() && idempotentConsumer == null) {
                throw new IllegalStateException(consumer.getClass().getName()
                        + " is idempotent but no IdempotentConsumer is available: set ddk.event.inbox.enabled=true");
            }
        }
    }

    public List<IntegrationEventConsumer<?>> consumers() {
        return consumers;
    }

    /**
     * 分发给指定消费组里订阅了这个主题和标签的全部消费方。任何一个消费方失败都会抛出异常，由调用方决定是否重投；
     * 重投时已经成功的消费方会再执行一次，这正是处理逻辑必须可重复执行的原因。
     *
     * @return 处理了这条消息的消费方数量
     */
    public int dispatch(String group, String topic, @Nullable String tag, Map<String, String> headers, String json) {
        int handled = 0;
        for (IntegrationEventConsumer<?> consumer : consumers) {
            if (consumer.group().equals(group) && matches(consumer.source(), topic, tag)) {
                invoke(consumer, headers, json);
                handled++;
            }
        }
        return handled;
    }

    private <T> void invoke(IntegrationEventConsumer<T> consumer, Map<String, String> headers, String json) {
        String eventId = headers.get(EVENT_ID_HEADER);
        ReceivedEvent<T> event = new ReceivedEvent<>(eventId, headers.get(EVENT_TYPE_HEADER), version(headers),
                jsonMapper.readValue(json, consumer.payloadType()));
        if (!consumer.idempotent() || idempotentConsumer == null) {
            consumer.handle(event);
            return;
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalStateException(consumer.getClass().getName() + " is idempotent but the message has no " + EVENT_ID_HEADER
                    + " header: declare @IntegrationEvent(id = ...) on the published event");
        }
        boolean processed = idempotentConsumer.handle(consumer.group() + ":" + consumer.getClass().getSimpleName(), eventId,
                () -> consumer.handle(event));
        if (!processed) {
            log.debug("Skipped duplicate event [{}] for {}", eventId, consumer.getClass().getSimpleName());
        }
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
