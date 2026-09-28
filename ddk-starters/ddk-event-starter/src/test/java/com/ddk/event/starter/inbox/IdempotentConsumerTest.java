package com.ddk.event.starter.inbox;

import com.ddk.event.starter.integration.IntegrationEventExternalizationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = IntegrationEventExternalizationTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:inbox;DB_CLOSE_DELAY=-1",
        "ddk.event.inbox.enabled=true"
})
class IdempotentConsumerTest {

    @Autowired
    private IdempotentConsumer consumer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    private final AtomicInteger handled = new AtomicInteger();

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM ddk_processed_message");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mail_log (message_id VARCHAR(50))");
        jdbc.update("DELETE FROM mail_log");
    }

    @Test
    void handlesAMessageOncePerConsumer() {
        assertThat(consumer.handle("welcome-mail", "evt-1", handled::incrementAndGet)).isTrue();
        assertThat(consumer.handle("welcome-mail", "evt-1", handled::incrementAndGet)).isFalse();
        assertThat(consumer.handle("points", "evt-1", handled::incrementAndGet)).isTrue();

        assertThat(handled).hasValue(2);
        assertThat(consumer.isProcessed("welcome-mail", "evt-1")).isTrue();
    }

    @Test
    void aFailedHandlerLeavesTheMessageUnprocessedSoARedeliveryRunsAgain() {
        assertThatThrownBy(() -> consumer.handle("welcome-mail", "evt-2", () -> {
            jdbc.update("INSERT INTO mail_log VALUES ('evt-2')");
            throw new IllegalStateException("smtp down");
        })).hasMessage("smtp down");

        assertThat(consumer.isProcessed("welcome-mail", "evt-2")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_log", Integer.class)).isZero();

        assertThat(consumer.handle("welcome-mail", "evt-2", handled::incrementAndGet)).isTrue();
    }

    @Test
    void aDuplicateInsideAnOuterTransactionDoesNotSpoilIt() {
        consumer.handle("welcome-mail", "evt-3", handled::incrementAndGet);

        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO mail_log VALUES ('other-work')");
            assertThat(consumer.handle("welcome-mail", "evt-3", handled::incrementAndGet)).isFalse();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_log", Integer.class)).isEqualTo(1);
        assertThat(handled).hasValue(1);
    }

    @Test
    void purgesOldRecords() {
        consumer.handle("welcome-mail", "evt-4", handled::incrementAndGet);

        assertThat(consumer.purgeOlderThan(Duration.ofDays(1))).isZero();
        assertThat(consumer.purgeOlderThan(Duration.ofSeconds(-1))).isEqualTo(1);
    }

    @Test
    void rejectsUnsafeTableNames() {
        assertThatThrownBy(() -> new IdempotentConsumer(jdbc, null, "t; DROP TABLE users", java.time.Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
