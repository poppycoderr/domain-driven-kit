package com.ddk.archguard.starter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;

public class ArchGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ArchGuardAutoConfiguration.class));

    @Test
    void archGuardAutoConfigurationShouldBeLoaded() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ArchGuardAutoConfiguration.class);
        });
    }

    @Test
    void common_arch_rules_should_be_accessible() {
        assertThat(CommonArchRules.LAYERED_ARCHITECTURE_RULE).isNotNull();

        JavaClasses importedClasses = new ClassFileImporter().importClasses(CommonArchRules.class);
        assertThat(importedClasses).isNotEmpty();
    }
}
