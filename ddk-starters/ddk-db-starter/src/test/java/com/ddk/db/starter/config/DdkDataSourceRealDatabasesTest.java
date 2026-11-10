package com.ddk.db.starter.config;

import com.ddk.test.containers.DdkContainers;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 两个真实数据库（PostgreSQL 和 MySQL）各配成一个数据源：连到各自的库，主数据源按配置选出，每个事务管理器只管自己的库。
 */
@Testcontainers(disabledWithoutDocker = true)
class DdkDataSourceRealDatabasesTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    private static final AtomicInteger TABLES = new AtomicInteger();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkDataSourceAutoConfiguration.class,
                    DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class))
            .withPropertyValues(
                    "ddk.datasource.primary=orders",
                    "ddk.datasource.sources.orders.url=" + POSTGRES.getJdbcUrl(),
                    "ddk.datasource.sources.orders.username=" + POSTGRES.getUsername(),
                    "ddk.datasource.sources.orders.password=" + POSTGRES.getPassword(),
                    "ddk.datasource.sources.orders.pool.pool-name=orders-pool",
                    "ddk.datasource.sources.audit.url=" + MYSQL.getJdbcUrl(),
                    "ddk.datasource.sources.audit.username=" + MYSQL.getUsername(),
                    "ddk.datasource.sources.audit.password=" + MYSQL.getPassword(),
                    "ddk.datasource.sources.audit.pool.pool-name=audit-pool");

    @Test
    @DisplayName("每个数据源连到自己的数据库，主数据源是配置里指定的那个")
    void eachSourceReachesItsOwnDatabase() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            JdbcTemplate orders = context.getBean("ordersJdbcTemplate", JdbcTemplate.class);
            JdbcTemplate audit = context.getBean("auditJdbcTemplate", JdbcTemplate.class);

            assertThat(orders.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL");
            assertThat(audit.queryForObject("SELECT @@version_comment", String.class)).containsIgnoringCase("MySQL");
            assertThat(context.getBean(JdbcTemplate.class)).isSameAs(orders);
            assertThat(context.getBean(PlatformTransactionManager.class)).isSameAs(context.getBean("ordersTransactionManager"));
            assertThat(context.getBean("ordersDataSource", HikariDataSource.class).getPoolName()).isEqualTo("orders-pool");
            assertThat(context.getBean("auditDataSource", HikariDataSource.class).getPoolName()).isEqualTo("audit-pool");
        });
    }

    @Test
    @DisplayName("回滚一个数据源的事务只撤销这个库里的写入：另一个库的写入不在这个事务里")
    void aTransactionManagerOnlyCoversItsOwnSource() {
        runner.run(context -> {
            String table = "t_tx_" + TABLES.incrementAndGet();
            JdbcTemplate orders = context.getBean("ordersJdbcTemplate", JdbcTemplate.class);
            JdbcTemplate audit = context.getBean("auditJdbcTemplate", JdbcTemplate.class);
            orders.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY)");
            audit.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY) ENGINE=InnoDB");

            TransactionTemplate ordersTx = new TransactionTemplate(context.getBean("ordersTransactionManager", PlatformTransactionManager.class));
            assertThatThrownBy(() -> ordersTx.executeWithoutResult(status -> {
                orders.update("INSERT INTO " + table + " (id) VALUES (1)");
                audit.update("INSERT INTO " + table + " (id) VALUES (1)");
                throw new IllegalStateException("roll back");
            })).hasMessage("roll back");

            assertThat(orders.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).as("rolled back").isZero();
            assertThat(audit.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class))
                    .as("audit was written outside the orders transaction and stays").isEqualTo(1);

            TransactionTemplate auditTx = new TransactionTemplate(context.getBean("auditTransactionManager", PlatformTransactionManager.class));
            assertThatThrownBy(() -> auditTx.executeWithoutResult(status -> {
                audit.update("INSERT INTO " + table + " (id) VALUES (2)");
                throw new IllegalStateException("roll back");
            })).hasMessage("roll back");
            assertThat(audit.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class))
                    .as("the audit transaction manager rolls back its own source").isEqualTo(1);
        });
    }
}
