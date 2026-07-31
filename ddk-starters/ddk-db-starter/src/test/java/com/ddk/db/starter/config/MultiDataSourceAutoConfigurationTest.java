package com.ddk.db.starter.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class, // Spring Boot's standard DataSource auto-config
                    TransactionAutoConfiguration.class, // Spring Boot's standard Transaction auto-config
                    MultiDataSourceAutoConfiguration.class // Our custom auto-configuration
            ));

    @Test
    void whenSingleDataSourceConfigured_thenBeansAreRegisteredAsPrimary() {
        contextRunner.withPropertyValues(
                "ddk.datasource.primary=primaryDb",
                "ddk.datasource.sources[0].name=primaryDb",
                "ddk.datasource.sources[0].url=jdbc:h2:mem:primaryDbTest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ddk.datasource.sources[0].driverClassName=org.h2.Driver",
                "ddk.datasource.sources[0].username=sa",
                "ddk.datasource.sources[0].password=",
                "ddk.datasource.sources[0].type=com.zaxxer.hikari.HikariDataSource"
        ).run(context -> {
            assertThat(context).hasBean("primaryDbDataSource");
            DataSource dataSource = context.getBean("primaryDbDataSource", DataSource.class);
            assertThat(dataSource).isInstanceOf(HikariDataSource.class);
            assertThat(context.getBean(DataSource.class)).isSameAs(dataSource); // Check primary

            assertThat(context).hasBean("primaryDbTransactionManager");
            PlatformTransactionManager txManager = context.getBean("primaryDbTransactionManager", PlatformTransactionManager.class);
            assertThat(context.getBean(PlatformTransactionManager.class)).isSameAs(txManager); // Check primary

            assertThat(context).hasBean("primaryDbJdbcTemplate");
            JdbcTemplate jdbcTemplate = context.getBean("primaryDbJdbcTemplate", JdbcTemplate.class);
            assertThat(context.getBean(JdbcTemplate.class)).isSameAs(jdbcTemplate); // Check primary

            // Verify basic DB operation
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            assertThat(result).isEqualTo(1);
        });
    }

    @Test
    void whenMultipleDataSourcesConfigured_thenAllBeansAreRegistered() {
        contextRunner.withPropertyValues(
                "ddk.datasource.primary=mainDb",
                "ddk.datasource.sources[0].name=mainDb",
                "ddk.datasource.sources[0].url=jdbc:h2:mem:mainDbTest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ddk.datasource.sources[0].driverClassName=org.h2.Driver",
                "ddk.datasource.sources[0].username=sa",
                "ddk.datasource.sources[0].password=",
                "ddk.datasource.sources[0].type=com.zaxxer.hikari.HikariDataSource",
                "ddk.datasource.sources[1].name=auditDb",
                "ddk.datasource.sources[1].url=jdbc:h2:mem:auditDbTest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ddk.datasource.sources[1].driverClassName=org.h2.Driver",
                "ddk.datasource.sources[1].username=sa",
                "ddk.datasource.sources[1].password=",
                "ddk.datasource.sources[1].type=com.zaxxer.hikari.HikariDataSource"
        ).run(context -> {
            // Check mainDb (Primary)
            assertThat(context).hasBean("mainDbDataSource");
            DataSource mainDataSource = context.getBean("mainDbDataSource", DataSource.class);
            assertThat(context.getBean(DataSource.class)).isSameAs(mainDataSource);

            assertThat(context).hasBean("mainDbTransactionManager");
            PlatformTransactionManager mainTxManager = context.getBean("mainDbTransactionManager", PlatformTransactionManager.class);
            assertThat(context.getBean(PlatformTransactionManager.class)).isSameAs(mainTxManager);

            assertThat(context).hasBean("mainDbJdbcTemplate");
            JdbcTemplate mainJdbcTemplate = context.getBean("mainDbJdbcTemplate", JdbcTemplate.class);
            assertThat(context.getBean(JdbcTemplate.class)).isSameAs(mainJdbcTemplate);
            assertThat(mainJdbcTemplate.queryForObject("SELECT 10", Integer.class)).isEqualTo(10);

            // Check auditDb (Secondary)
            assertThat(context).hasBean("auditDbDataSource");
            DataSource auditDataSource = context.getBean("auditDbDataSource", DataSource.class);
            assertThat(auditDataSource).isNotSameAs(mainDataSource);

            assertThat(context).hasBean("auditDbTransactionManager");
            PlatformTransactionManager auditTxManager = context.getBean("auditDbTransactionManager", PlatformTransactionManager.class);
            assertThat(auditTxManager).isNotSameAs(mainTxManager);

            assertThat(context).hasBean("auditDbJdbcTemplate");
            JdbcTemplate auditJdbcTemplate = context.getBean("auditDbJdbcTemplate", JdbcTemplate.class);
            assertThat(auditJdbcTemplate).isNotSameAs(mainJdbcTemplate);
            assertThat(auditJdbcTemplate.queryForObject("SELECT 20", Integer.class)).isEqualTo(20);

            // Verify primary beans are not the audit beans
            ApplicationContext ac = (ApplicationContext) context;
            assertThat(ac.getBean(DataSource.class)).isNotSameAs(auditDataSource);
            assertThat(ac.getBean(PlatformTransactionManager.class)).isNotSameAs(auditTxManager);
            assertThat(ac.getBean(JdbcTemplate.class)).isNotSameAs(auditJdbcTemplate);
        });
    }

    @Test
    void whenPrimaryIsMissing_thenAutoConfigurationDoesNotRun() {
        contextRunner.withPropertyValues(
                // "ddk.datasource.primary" is missing
                "ddk.datasource.sources[0].name=someDb",
                "ddk.datasource.sources[0].url=jdbc:h2:mem:someDbTest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ddk.datasource.sources[0].driverClassName=org.h2.Driver"
        ).run(context -> {
            // Because MultiDataSourceAutoConfiguration is conditional on "ddk.datasource.primary",
            // it should not register any of its beans.
            assertThat(context).doesNotHaveBean("someDbDataSource");
            assertThat(context).doesNotHaveBean("someDbTransactionManager");
            assertThat(context).doesNotHaveBean("someDbJdbcTemplate");

            // 注意：这里不能断言「完全没有 DataSource」。H2 在测试 classpath 上，
            // Spring Boot 的 DataSourceAutoConfiguration 会自动创建一个内嵌数据源，
            // 这与本 starter 是否生效无关。正确的断言是：存在的那个 DataSource
            // 来自 Spring Boot（Bean 名为 dataSource），而不是本 starter 注册的。
            assertThat(context).hasBean("dataSource");
            assertThat(context.getBeanNamesForType(DataSource.class)).containsExactly("dataSource");
        });
    }
}
