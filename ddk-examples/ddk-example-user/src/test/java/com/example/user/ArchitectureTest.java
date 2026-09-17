package com.example.user;

import com.ddk.archguard.starter.report.ArchGuard;
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
    void architectureIsRespected() {
        ArchGuard.check(classes,
                CommonArchRules.LAYERED_ARCHITECTURE_RULE,
                CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS,
                CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS,
                CommonArchRules.DDK_INTERNALS_MUST_NOT_BE_USED,
                CommonArchRules.MCP_TOOLS_MUST_RESIDE_IN_ADAPTER);
    }
}
