package com.ddk;

import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 架构约束的可执行版本。
 * <p>
 * 这是 {@code ddk-archguard-starter} 的标准用法，也是这个骨架最该被抄走的一个文件：
 * 分层规则写在文档里只是建议，写成测试才是约束——违反时构建直接失败。
 *
 * @author Elijah Du
 */
@DisplayName("四层架构约束")
class ArchitectureTest {

    /**
     * 两个 ImportOption 都是必须的，缺一个规则就会报出无意义的违规：
     * <ul>
     *     <li>{@code DO_NOT_INCLUDE_JARS}：只分析自己编译出来的类。
     *     不加的话，classpath 上包名恰好落进 {@code ..domain..} 等层匹配的
     *     第三方类（DDK 自己的 {@code com.ddk.core.domain} 就是）也会被算进去</li>
     *     <li>{@code DO_NOT_INCLUDE_TESTS}：测试类经常为了构造场景而跨层引用</li>
     * </ul>
     */
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ddk");

    @Test
    @DisplayName("依赖方向：UI -> Application -> Domain，Infrastructure 只反向实现领域契约")
    void layeredArchitectureIsRespected() {
        CommonArchRules.LAYERED_ARCHITECTURE_RULE.check(classes);
    }

    @Test
    @DisplayName("领域层保持框架无关，能脱离容器单测")
    void domainStaysFrameworkFree() {
        CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
    }

    @Test
    @DisplayName("领域层不反向依赖外层")
    void domainDoesNotDependOnOuterLayers() {
        CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS.check(classes);
    }
}
