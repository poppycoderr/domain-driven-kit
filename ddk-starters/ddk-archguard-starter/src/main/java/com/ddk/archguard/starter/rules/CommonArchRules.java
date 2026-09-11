package com.ddk.archguard.starter.rules;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD 分层架构的通用 ArchUnit 规则。
 * <p>
 * 这些规则的意义在于：架构约束写在文档里只是建议，写成测试才是约束。
 * 下游项目以 {@code <scope>test</scope>} 引入本 starter，在自己的测试里引用这些常量即可。
 *
 * <pre>{@code
 * class ArchitectureTest {
 *     private final JavaClasses classes =
 *             new ClassFileImporter().importPackages("com.example.myapp");
 *
 *     @Test
 *     void layered_architecture_is_respected() {
 *         CommonArchRules.LAYERED_ARCHITECTURE_RULE.check(classes);
 *     }
 *
 *     @Test
 *     void domain_stays_framework_free() {
 *         CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
 *     }
 * }
 * }</pre>
 *
 * @author Elijah Du
 */
public final class CommonArchRules {

    private CommonArchRules() {
    }

    /**
     * 框架包前缀。领域层不得依赖其中任何一个。
     */
    static final String[] FRAMEWORK_PACKAGES = {
            "org.springframework..",
            "com.baomidou..",
            "com.fasterxml.jackson..",
            "org.apache.ibatis..",
            "jakarta.persistence..",
            "javax.persistence..",
    };

    /**
     * 四层依赖方向：UI -> Application -> Domain，Infrastructure 只为实现领域契约而反向依赖 Domain。
     */
    public static final ArchRule LAYERED_ARCHITECTURE_RULE = Architectures.layeredArchitecture()
            .consideringAllDependencies()
            .layer("UI").definedBy("..ui..", "..adapter..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("Infrastructure").definedBy("..infrastructure..")

            .whereLayer("UI").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("UI")
            // Infrastructure 可以访问 Domain，因为仓储实现要实现领域层定义的接口
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Application");

    /**
     * 领域层必须保持框架无关。
     * <p>
     * 这条规则保护的是<b>可测试性</b>：领域模型一旦依赖 Spring 或 ORM，
     * 就必须起容器才能测，单元测试会退化成集成测试；
     * 同时持久化细节会顺着注解渗进业务模型，让「表结构」反过来决定「业务形状」。
     */
    public static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
            .because("领域模型必须能在没有容器的情况下被单元测试，持久化细节不得渗入业务模型");

    /**
     * 领域层不得依赖应用层与适配层。
     * <p>
     * {@link #LAYERED_ARCHITECTURE_RULE} 已经覆盖了这一点，
     * 单独提供一条是为了让只想约束领域层纯度的项目可以按需选用。
     */
    public static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..adapter..", "..ui..", "..infrastructure..")
            .because("依赖方向必须指向内层，领域层是依赖图的终点");
}
