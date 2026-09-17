package com.ddk.archguard.starter.report;

import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchGuardTest {

    private static final String VIOLATING_ORDER = "com.ddk.archguard.fixture.four.violation.domain.Order";

    private final JavaClasses violation = new ClassFileImporter().importPackages("com.ddk.archguard.fixture.four.violation");

    private final JavaClasses valid = new ClassFileImporter().importPackages("com.ddk.archguard.fixture.four.valid");

    @Test
    void evaluatesEveryRuleInsteadOfStoppingAtTheFirstFailure() {
        ArchGuardReport report = ArchGuard.evaluate(violation,
                CommonArchRules.LAYERED_ARCHITECTURE_RULE, CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS);

        assertThat(report.violations()).extracting(Violation::ruleId)
                .contains("LAYERED_ARCHITECTURE_RULE", "DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS");
        assertThat(report.violations()).extracting(Violation::className).containsOnly(VIOLATING_ORDER);
        assertThat(report.violations()).allSatisfy(v -> assertThat(v.hint()).contains("domain.acl"));
    }

    @Test
    void failingCheckWritesReportsAndExplainsHowToFix(@TempDir Path dir) throws Exception {
        assertThatThrownBy(() -> ArchGuard.check(violation, dir, CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("[DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS] " + VIOLATING_ORDER)
                .hasMessageContaining("How to fix:")
                .hasMessageContaining(dir.resolve(ArchGuardReport.MARKDOWN_FILE).toString());

        String json = Files.readString(dir.resolve(ArchGuardReport.JSON_FILE));
        assertThat(json).contains("\"violationCount\": 1").contains("\"class\": \"" + VIOLATING_ORDER + "\"");
        assertThat(Files.readString(dir.resolve(ArchGuardReport.MARKDOWN_FILE)))
                .contains("## DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS").contains("**How to fix**");
    }

    @Test
    void passingCheckStillWritesAnEmptyReport(@TempDir Path dir) throws Exception {
        ArchGuardReport report = ArchGuard.check(valid, dir, CommonArchRules.LAYERED_ARCHITECTURE_RULE);

        assertThat(report.hasViolations()).isFalse();
        assertThat(Files.readString(dir.resolve(ArchGuardReport.JSON_FILE))).contains("\"violationCount\": 0");
    }

    @Test
    void customRulesGetAGenericHint() {
        ArchRule custom = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

        assertThat(ArchGuard.evaluate(violation, custom).violations())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.ruleId()).isEqualTo("CUSTOM");
                    assertThat(v.className()).isEqualTo(VIOLATING_ORDER);
                });
    }

    @Test
    void jsonEscapesQuotesAndLineBreaks() {
        Violation v = new Violation("CUSTOM", "rule \"x\"", null, "line1\nline2\\", "hint");

        assertThat(new ArchGuardReport(List.of(v)).toJson())
                .contains("\"class\": null")
                .contains("\"detail\": \"line1\\nline2\\\\\"")
                .contains("\"ruleDescription\": \"rule \\\"x\\\"\"");
    }
}
