package com.example.user;

import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.user");

    @Test
    void layeredArchitectureIsRespected() {
        CommonArchRules.LAYERED_ARCHITECTURE_RULE.check(classes);
    }

    @Test
    void domainStaysFrameworkFree() {
        CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
    }

    @Test
    void domainDoesNotDependOnOuterLayers() {
        CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS.check(classes);
    }

    @Test
    void ddkInternalsAreNotUsed() {
        CommonArchRules.DDK_INTERNALS_MUST_NOT_BE_USED.check(classes);
    }
}
