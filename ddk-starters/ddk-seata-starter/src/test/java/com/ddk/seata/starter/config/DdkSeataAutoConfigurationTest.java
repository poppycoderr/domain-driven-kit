package com.ddk.seata.starter.config;

import io.seata.rm.datasource.DataSourceProxy;
import io.seata.spring.annotation.GlobalTransactionScanner;
import io.seata.spring.boot.autoconfigure.properties.SeataProperties; // For checking Seata's own properties bean
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class DdkSeataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    // For tests needing a DataSource, DataSourceAutoConfiguration.class is added dynamically
                    DdkSeataAutoConfiguration.class
            ));

    @Test
    void whenSeataEnabled_thenCoreSeataBeansAreRegistered() {
        contextRunner.withPropertyValues(
                "seata.enabled=true",
                "seata.application-id=test-app",
                "seata.tx-service-group=my_test_tx_group",
                // Minimal Seata server config for client initialization
                "seata.service.vgroup-mapping.my_test_tx_group=default",
                "seata.service.grouplist.default=127.0.0.1:8091", // Dummy address, won't connect
                "seata.registry.type=file", // Use file based registry/config for tests
                "seata.config.type=file"
        ).run(context -> {
            assertThat(context).hasSingleBean(DdkSeataAutoConfiguration.class);
            // Check for Seata's own auto-configuration properties bean
            assertThat(context).hasSingleBean(SeataProperties.class);
            // Check for the GlobalTransactionScanner, a key component
            assertThat(context).hasSingleBean(GlobalTransactionScanner.class);
            // Check that Seata's main auto-configuration class was imported and processed
            assertThat(context).hasSingleBean(io.seata.spring.boot.autoconfigure.SeataAutoConfiguration.class);
        });
    }

    @Test
    void whenSeataEnabledAndDataSourcePresent_thenDataSourceIsProxied() {
        new ApplicationContextRunner() // Use a fresh runner for different auto-config setup
            .withConfiguration(AutoConfigurations.of(
                DataSourceAutoConfiguration.class, // Standard Spring Boot DataSource config
                DdkSeataAutoConfiguration.class  // Our Seata starter
            ))
            .withPropertyValues(
                "seata.enabled=true",
                "seata.application-id=test-app-ds",
                "seata.tx-service-group=my_test_ds_group",
                "seata.service.vgroup-mapping.my_test_ds_group=default",
                "seata.service.grouplist.default=127.0.0.1:8091",
                "seata.registry.type=file",
                "seata.config.type=file",
                // Configure a basic H2 data source
                "spring.datasource.url=jdbc:h2:mem:testseatads;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver"
                // Seata's default is to auto-proxy.
                // We can also explicitly set: "seata.data-source-proxy-mode=AT" (or XA)
            ).run(context -> {
                assertThat(context).hasSingleBean(DdkSeataAutoConfiguration.class);
                assertThat(context).hasSingleBean(GlobalTransactionScanner.class);
                assertThat(context).hasSingleBean(DataSource.class);
                DataSource dataSourceInContext = context.getBean(DataSource.class);
                // Verify that the DataSource bean is proxied by Seata
                assertThat(dataSourceInContext).isInstanceOf(DataSourceProxy.class);
            });
    }

    @Test
    void whenSeataDisabled_thenNoSeataBeansAreRegistered() {
        contextRunner.withPropertyValues(
                "seata.enabled=false"
                // No other seata properties needed as it should be disabled
        ).run(context -> {
            // DdkSeataAutoConfiguration might still be present because its own conditions might pass
            // but it imports SeataAutoConfiguration which is conditional on seata.enabled=true
            assertThat(context).hasSingleBean(DdkSeataAutoConfiguration.class);

            // Core Seata beans should NOT be present
            assertThat(context).doesNotHaveBean(GlobalTransactionScanner.class);
            assertThat(context).doesNotHaveBean(SeataProperties.class);
            // Seata's main auto-configuration should not be processed due to its own internal conditions
            assertThat(context).doesNotHaveBean(io.seata.spring.boot.autoconfigure.SeataAutoConfiguration.class);
        });
    }
}
