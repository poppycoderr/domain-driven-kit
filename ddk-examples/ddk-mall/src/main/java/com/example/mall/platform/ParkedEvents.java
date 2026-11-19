package com.example.mall.platform;

import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.consumer.UndeliverableEvent;
import com.ddk.event.starter.consumer.UndeliverableEventHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 把怎么也消费不了的消息停到 {@code t_parked_event} 表里，让它所在的队列继续往下走。compose profile 设置了失败上限后生效。
 * <p>
 * 停下的消息不会自动重发：修好原因之后，由人看过再决定重发还是丢弃。写表失败时异常照常抛出，消息留在队列里，下次失败再试。
 */
@Component
class ParkedEvents implements UndeliverableEventHandler {

    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbc;

    ParkedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void handle(UndeliverableEvent event) {
        jdbc.update("INSERT INTO t_parked_event (consumer_group, topic, tag, message_id, event_id, body, attempts, last_error, parked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                event.group(), event.topic(), event.tag(), event.messageId(), event.headers().get(IntegrationEventDispatcher.EVENT_ID_HEADER),
                event.body(), event.attempts(), error(event.lastFailure()), Timestamp.from(Instant.now()));
    }

    private static String error(RuntimeException failure) {
        String text = failure.getClass().getName() + ": " + failure.getMessage();
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
