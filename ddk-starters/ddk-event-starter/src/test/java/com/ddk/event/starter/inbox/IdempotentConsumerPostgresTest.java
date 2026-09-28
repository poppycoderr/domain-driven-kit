package com.ddk.event.starter.inbox;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL 在语句失败后会把整个事务标记为中止，之后的语句全部报错、提交变成回滚。
 * 这里验证重复登记只回滚到保存点：外层事务里其他写入照常提交。
 */
@Testcontainers(disabledWithoutDocker = true)
class IdempotentConsumerPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static JdbcTemplate jdbc;

    private static TransactionTemplate transaction;

    private static IdempotentConsumer consumer;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        consumer = new IdempotentConsumer(jdbc, transactionManager, "ddk_processed_message", Clock.systemUTC());
        jdbc.execute(consumer.schema());
        jdbc.execute("CREATE TABLE mail_log (message_id VARCHAR(50))");
    }

    @Test
    void aDuplicateInsideAnOuterTransactionKeepsTheOuterWork() {
        AtomicInteger handled = new AtomicInteger();
        consumer.handle("welcome-mail", "evt-1", handled::incrementAndGet);

        transaction.executeWithoutResult(status -> {
            assertThat(consumer.handle("welcome-mail", "evt-1", handled::incrementAndGet)).isFalse();
            jdbc.update("INSERT INTO mail_log VALUES ('after-duplicate')");
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_log", Integer.class)).isEqualTo(1);
        assertThat(handled).hasValue(1);
    }
}
