package com.ddk.seata.starter.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

// It's good practice to explicitly import Seata's main auto-configuration
// if our starter is meant to build upon or ensure its presence.
// Note: The actual class name for Seata's auto-config might vary slightly by version,
// e.g., it could be SeataDataSourceAutoConfiguration or similar for specific parts.
// Assuming io.seata.spring.boot.autoconfigure.SeataAutoConfiguration is the main one.
// If Seata modularizes its auto-configs further, more specific imports might be needed.
import io.seata.spring.boot.autoconfigure.SeataAutoConfiguration;


@AutoConfiguration
@ConditionalOnClass(name = {
        "io.seata.spring.annotation.GlobalTransactionScanner", // Core component for @GlobalTransactional
        "io.seata.tm.TMClient", // Transaction Manager Client
        "io.seata.rm.RMClient"  // Resource Manager Client
})
@ConditionalOnProperty(name = "seata.enabled", havingValue = "true", matchIfMissing = true)
@Import(SeataAutoConfiguration.class) // Explicitly import Seata's own auto-configuration
public class DdkSeataAutoConfiguration {

    // This class serves as a DDK-specific entry point for Seata integration.
    // It ensures that Seata's core components are on the classpath and that Seata is enabled
    // via the "seata.enabled" property (defaulting to true if the property is missing).

    // By importing SeataAutoConfiguration.class, we ensure that Seata's default
    // auto-configuration logic is triggered, which will set up beans like
    // GlobalTransactionScanner, configure data source proxies (if seata.data-source-proxy-mode is set),
    // and handle other Seata-specific initializations.

    // Future DDK-specific customizations related to Seata could be added here, such as:
    // - Defaulting certain Seata properties if not set by the user.
    // - Registering DDK-specific interceptors or listeners for Seata events.
    // - Providing simplified configuration for specific use cases within the DDK framework.

}
