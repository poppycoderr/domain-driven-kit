package com.ddk.archguard.starter.rules;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD 分层架构的通用 ArchUnit 规则。
 * <p>
 * 这些规则的意义在于：架构约束写在文档里只是建议，写成测试才是约束。
 * 下游项目以 {@code <scope>test</scope>} 引入本 starter，在自己的测试里引用这些常量即可。
 *
 * <pre>{@code
 * class ArchitectureTest {
 *     // DO_NOT_INCLUDE_JARS 不能省：否则 classpath 上包名恰好落进
 *     // ..domain.. 等层匹配的第三方类也会被算进来
 *     private final JavaClasses classes = new ClassFileImporter()
 *             .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
 *             .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
 *             .importPackages("com.example.myapp");
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
     * <p>
     * 层允许为空：刚生成的骨架、或者没有适配层的纯后台服务，不会因为「某层没有类」而失败。
     */
    public static final ArchRule LAYERED_ARCHITECTURE_RULE = Architectures.layeredArchitecture()
            .consideringAllDependencies()
            .withOptionalLayers(true)
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
     * 三层依赖方向：Adapter -> Business，Infrastructure 实现 Business 定义的接口。
     * <p>
     * Business 合并了应用层与领域层，因此不要求它框架无关；约束的重点是依赖倒置——
     * 业务层与适配层都不能直接引用基础设施层的实现类。
     */
    public static final ArchRule THREE_LAYER_ARCHITECTURE_RULE = Architectures.layeredArchitecture()
            .consideringAllDependencies()
            .withOptionalLayers(true)
            .layer("Adapter").definedBy("..adapter..")
            .layer("Business").definedBy("..business..")
            .layer("Infrastructure").definedBy("..infrastructure..")

            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("Business").mayOnlyBeAccessedByLayers("Adapter", "Infrastructure")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

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
            .because("领域模型必须能在没有容器的情况下被单元测试，持久化细节不得渗入业务模型")
            .allowEmptyShould(true);

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
            .because("依赖方向必须指向内层，领域层是依赖图的终点")
            .allowEmptyShould(true);

    /**
     * 应用代码不得依赖 DDK 的 {@code internal} 包。
     * <p>
     * {@code internal} 包里是 starter 的实现细节，可能在任何版本中改名或删除；
     * 依赖它们的代码会在升级 DDK 时无声地坏掉。需要定制时，覆盖对应的 Bean 或实现公开接口。
     */
    public static final ArchRule DDK_INTERNALS_MUST_NOT_BE_USED = noClasses()
            .that().resideOutsideOfPackage("com.ddk..")
            .should().dependOnClassesThat().resideInAPackage("com.ddk..internal..")
            .because("DDK 的 internal 包不属于公开 API，可能在任何版本中变更")
            .allowEmptyShould(true);

    /**
     * MCP 工具只能声明在适配层。
     * <p>
     * MCP 工具与 REST 控制器一样是外部协议的入口。放在适配层后，分层规则保证它只能经由应用服务访问领域，
     * 模型因此拿不到仓储，也绕不过应用服务里的事务与业务校验。按注解全限定名匹配，本模块不依赖 Spring AI。
     */
    public static final ArchRule MCP_TOOLS_MUST_RESIDE_IN_ADAPTER = methods()
            .that().areAnnotatedWith("org.springframework.ai.mcp.annotation.McpTool")
            .should().beDeclaredInClassesThat().resideInAPackage("..adapter..")
            .because("MCP 工具是外部协议入口，只能经由应用服务访问领域")
            .allowEmptyShould(true);
}
