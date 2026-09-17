package ${package};

import com.ddk.archguard.starter.report.ArchGuard;
import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 分层规则写成测试，违反即构建失败。违规明细与修复建议写在 target/archguard/violations.md。
 */
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("${package}");

    @Test
    void architectureIsRespected() {
        ArchGuard.check(classes,
                CommonArchRules.LAYERED_ARCHITECTURE_RULE,
                CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS,
                CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS,
                CommonArchRules.DDK_INTERNALS_MUST_NOT_BE_USED);
    }
}
