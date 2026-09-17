package com.ddk.archguard.starter.report;

import org.jspecify.annotations.Nullable;

/**
 * 一条架构违规：违反了哪条规则、落在哪个类上、ArchUnit 的原始描述，以及修复建议。
 *
 * @param ruleId          规则标识，{@code CommonArchRules} 中的规则取常量名，其余规则为 {@code CUSTOM}
 * @param ruleDescription 规则的完整描述
 * @param className       违规所在类的全限定名；无法从描述中识别时为 null
 * @param detail          ArchUnit 给出的违规描述
 * @param hint            修复建议
 */
public record Violation(
        String ruleId,

        String ruleDescription,

        @Nullable String className,

        String detail,

        String hint
) {
}
