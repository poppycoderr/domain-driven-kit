package com.ddk.archguard.starter.rules;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

public final class CommonArchRules {

    private CommonArchRules() {
    }

    public static final ArchRule LAYERED_ARCHITECTURE_RULE = Architectures.layeredArchitecture()
            .consideringAllDependencies()
            .layer("UI").definedBy("..ui..", "..adapter..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("Infrastructure").definedBy("..infrastructure..")

            .whereLayer("UI").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("UI")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure") // Infrastructure can access Domain for repository implementations
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Application", "Domain");
}
