package com.ddk.db.starter.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DdkDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkDataSourceAutoConfiguration.class,
                    DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class));

    private static String h2(String db) {
        return "jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void singleSourceBecomesPrimaryWithoutNamingIt() {
        runner.withPropertyValues("ddk.datasource.sources.main.url=" + h2("single")).run(context -> {
            assertThat(context).hasNotFailed();
            DataSource dataSource = context.getBean("mainDataSource", DataSource.class);
            assertThat(context.getBean(DataSource.class)).isSameAs(dataSource);
            assertThat(context.getBean(PlatformTransactionManager.class))
                    .isSameAs(context.getBean("mainTransactionManager"))
                    .isInstanceOf(JdbcTransactionManager.class);
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
            // Boot 自己的单数据源配置退让
            assertThat(context).doesNotHaveBean("dataSource");
        });
    }

    @Test
    void registersEachSourceAndMarksThePrimary() {
        runner.withPropertyValues(
                "ddk.datasource.primary=main",
                "ddk.datasource.sources.main.url=" + h2("main"),
                "ddk.datasource.sources.audit.url=" + h2("audit")
        ).run(context -> {
            assertThat(context.getBeansOfType(DataSource.class)).containsOnlyKeys("mainDataSource", "auditDataSource");
            assertThat(context.getBean(DataSource.class)).isSameAs(context.getBean("mainDataSource"));
            assertThat(context.getBean(JdbcTemplate.class)).isSameAs(context.getBean("mainJdbcTemplate"));

            JdbcTemplate main = context.getBean("mainJdbcTemplate", JdbcTemplate.class);
            JdbcTemplate audit = context.getBean("auditJdbcTemplate", JdbcTemplate.class);
            main.execute("CREATE TABLE only_in_main (id INT)");
            assertThat(audit.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ONLY_IN_MAIN'", Integer.class))
                    .isZero();
        });
    }

    @Test
    void eachTransactionManagerCommitsOnItsOwnDataSource() {
        runner.withPropertyValues(
                "ddk.datasource.primary=main",
                "ddk.datasource.sources.main.url=" + h2("tx_main"),
                "ddk.datasource.sources.audit.url=" + h2("tx_audit")
        ).run(context -> {
            JdbcTemplate audit = context.getBean("auditJdbcTemplate", JdbcTemplate.class);
            audit.execute("CREATE TABLE audit_log (id INT)");
            TransactionTemplate auditTx = new TransactionTemplate(
                    context.getBean("auditTransactionManager", PlatformTransactionManager.class));

            auditTx.executeWithoutResult(status -> {
                audit.update("INSERT INTO audit_log VALUES (1)");
                status.setRollbackOnly();
            });

            assertThat(audit.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class)).isZero();
        });
    }

    @Test
    void bindsPoolPropertiesOntoThePoolInstance() {
        runner.withPropertyValues(
                "ddk.datasource.sources.main.url=" + h2("pool"),
                "ddk.datasource.sources.main.pool.maximum-pool-size=7",
                "ddk.datasource.sources.main.pool.pool-name=main-pool"
        ).run(context -> {
            HikariDataSource dataSource = context.getBean("mainDataSource", HikariDataSource.class);
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(7);
            assertThat(dataSource.getPoolName()).isEqualTo("main-pool");
        });
    }

    @Test
    void registersConcretePoolTypeBeforeInstantiation() {
        runner.withPropertyValues("ddk.datasource.sources.main.url=" + h2("typed")).run(context ->
                assertThat(context.getBeanFactory().getBeanNamesForType(HikariDataSource.class, false, false))
                        .containsExactly("mainDataSource"));
    }

    @Test
    void supportsDataSourcesWithoutCloseMethod() {
        runner.withPropertyValues(
                "ddk.datasource.sources.main.url=" + h2("simple"),
                "ddk.datasource.sources.main.driver-class-name=org.h2.Driver",
                "ddk.datasource.sources.main.type=" + SimpleDriverDataSource.class.getName()
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class)).isInstanceOf(SimpleDriverDataSource.class);
        });
    }

    @Test
    void failsFastWhenPrimaryIsMissingForSeveralSources() {
        runner.withPropertyValues(
                "ddk.datasource.sources.main.url=" + h2("a"),
                "ddk.datasource.sources.audit.url=" + h2("b")
        ).run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("ddk.datasource.primary must be set"));
    }

    @Test
    void failsFastWhenPrimaryMatchesNoSource() {
        runner.withPropertyValues(
                "ddk.datasource.primary=mian",
                "ddk.datasource.sources.main.url=" + h2("c")
        ).run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("'mian' does not match any configured data source"));
    }

    @Test
    void staysOutOfTheWayWithoutSources() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DdkDataSourceAutoConfiguration.class);
            assertThat(context.getBeanNamesForType(DataSource.class)).containsExactly("dataSource");
        });
    }
}
