package com.example.mall.platform;

import com.ddk.event.starter.consumer.UndeliverableEvent;
import com.ddk.event.starter.consumer.UndeliverableEventHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消费不了的消息停进 t_parked_event：表由 Flyway 建好，停下的内容足够人事后判断和重发。
 */
@SpringBootTest
class ParkedEventsTest {

    @Autowired
    private UndeliverableEventHandler handler;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void parksTheMessageWithWhatAPersonNeedsToDecide() {
        handler.handle(new UndeliverableEvent("inventory", "mall-order", "OrderPlaced", "msg-1", Map.of("ddk-event-id", "evt-42"),
                "{\"orderId\":1}", 10, new IllegalStateException("x".repeat(600))));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM t_parked_event WHERE message_id = 'msg-1'");
        assertThat(row).containsEntry("consumer_group", "inventory")
                .containsEntry("topic", "mall-order")
                .containsEntry("tag", "OrderPlaced")
                .containsEntry("event_id", "evt-42")
                .containsEntry("body", "{\"orderId\":1}")
                .containsEntry("attempts", 10);
        assertThat((String) row.get("last_error")).startsWith("java.lang.IllegalStateException: xxx").hasSize(500);
        assertThat(row.get("parked_at")).isNotNull();
    }
}
