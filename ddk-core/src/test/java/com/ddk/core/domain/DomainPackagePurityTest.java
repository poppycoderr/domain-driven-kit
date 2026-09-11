package com.ddk.core.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@code com.ddk.core.domain} 的硬约束：不得依赖任何框架。
 * <p>
 * 这条约束写在 package-info 与文档里只是声明，写成测试才是约束。
 * 一旦有人在领域基类上加了 {@code @Component} 或 Jackson 注解，这里会立刻失败。
 */
@DisplayName("领域模型包纯度")
class DomainPackagePurityTest {

    private final JavaClasses domainClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ddk.core.domain");

    @Test
    @DisplayName("不依赖 Spring、MyBatis、Jackson 等任何框架")
    void isFrameworkFree() {
        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "com.baomidou..",
                        "com.fasterxml.jackson..",
                        "org.apache.ibatis..",
                        "org.mapstruct..",
                        "jakarta.validation..",
                        "lombok..",
                        "cn.hutool..")
                .because("领域模型必须能在没有容器的情况下被单元测试")
                .check(domainClasses);
    }

    @Test
    @DisplayName("不依赖 ddk-core 的其他包，领域模型是依赖图的终点")
    void doesNotDependOnOtherCorePackages() {
        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ddk.core.mapper..",
                        "com.ddk.core.page..",
                        "com.ddk.core.repository..",
                        "com.ddk.core.response..")
                .because("领域模型不应知道分页、响应封装或持久化契约的存在")
                .check(domainClasses);
    }

    @Test
    @DisplayName("只依赖 JDK 与自身")
    void dependsOnlyOnJdkAndItself() {
        classes()
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("com.ddk.core.domain..", "java..")
                .because("领域基类不引入任何第三方依赖，下游可以放心继承")
                .check(domainClasses);
    }
}
