package com.ddk.archguard.starter.rules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonArchRulesTest {

    private static JavaClasses fixture(String name) {
        return new ClassFileImporter().importPackages("com.ddk.archguard.fixture." + name);
    }

    private static void passes(ArchRule rule, String fixture) {
        assertThatCode(() -> rule.check(fixture(fixture))).doesNotThrowAnyException();
    }

    private static void fails(ArchRule rule, String fixture, String violatingClass) {
        assertThatThrownBy(() -> rule.check(fixture(fixture))).isInstanceOf(AssertionError.class).hasMessageContaining(violatingClass);
    }

    @Test
    void fourLayerRuleAcceptsDependencyInversion() {
        passes(CommonArchRules.LAYERED_ARCHITECTURE_RULE, "four.valid");
    }

    @Test
    void fourLayerRuleRejectsDomainDependingOnInfrastructure() {
        fails(CommonArchRules.LAYERED_ARCHITECTURE_RULE, "four.violation", "four.violation.domain.Order");
        fails(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS, "four.violation", "four.violation.domain.Order");
    }

    @Test
    void domainPurityRuleRejectsFrameworkAnnotations() {
        fails(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS, "four.framework", "four.framework.domain.Order");
        passes(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS, "four.valid");
    }

    @Test
    void threeLayerRuleAcceptsDependencyInversion() {
        passes(CommonArchRules.THREE_LAYER_ARCHITECTURE_RULE, "three.valid");
    }

    @Test
    void threeLayerRuleRejectsBusinessDependingOnInfrastructure() {
        fails(CommonArchRules.THREE_LAYER_ARCHITECTURE_RULE, "three.violation", "three.violation.business.AccountService");
    }

    @Test
    void rulesPassOnFreshProjectsWithoutLayerClasses() {
        passes(CommonArchRules.LAYERED_ARCHITECTURE_RULE, "empty");
        passes(CommonArchRules.THREE_LAYER_ARCHITECTURE_RULE, "empty");
        passes(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS, "empty");
        passes(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS, "empty");
    }
}
