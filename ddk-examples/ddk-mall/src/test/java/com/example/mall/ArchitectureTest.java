package com.example.mall;

import com.ddk.archguard.starter.report.ArchGuard;
import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    /**
     * 限界上下文之间不直接引用对方的代码，只通过集成事件协作。{@code platform} 是各上下文共用的技术代码，不算上下文。
     */
    private static final ArchRule CONTEXTS_ARE_INDEPENDENT = slices()
            .matching("com.example.mall.(order|inventory|payment)..")
            .should().notDependOnEachOther()
            .allowEmptyShould(true);

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.mall");

    @Test
    void architectureIsRespected() {
        ArchGuard.check(classes,
                CommonArchRules.LAYERED_ARCHITECTURE_RULE,
                CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS,
                CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS,
                CommonArchRules.DDK_INTERNALS_MUST_NOT_BE_USED,
                CommonArchRules.SCHEDULED_JOBS_MUST_RESIDE_IN_ADAPTER,
                CommonArchRules.SCHEDULED_JOBS_MUST_BE_LOCKED,
                CONTEXTS_ARE_INDEPENDENT);
    }
}
