package com.ddk.event.starter.inbox;

import com.ddk.test.containers.DdkContainers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * MySQL 不支持 {@code CREATE INDEX IF NOT EXISTS}，这里验证到期索引按元数据判断后只建一次，分批清理的行值 {@code IN} 能执行。
 */
@Testcontainers(disabledWithoutDocker = true)
class IdempotentConsumerMysqlTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    private static JdbcTemplate jdbc;

    private static DataSourceTransactionManager transactionManager;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        transactionManager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void purgesInBatchesAndKeepsRecentRecords() {
        InboxCleanupChecks.purgesInBatchesAndKeepsRecentRecords(jdbc, transactionManager);
    }

    @Test
    void addsTheExpiryIndexOnlyWhenAllowed() {
        InboxCleanupChecks.addsTheExpiryIndexOnlyWhenAllowed(jdbc, transactionManager);
    }
}
