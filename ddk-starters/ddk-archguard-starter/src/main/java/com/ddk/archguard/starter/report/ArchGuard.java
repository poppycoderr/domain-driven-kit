package com.ddk.archguard.starter.report;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一次执行全部架构规则并输出违规报告。
 * <p>
 * 与逐条调用 {@code rule.check(classes)} 相比：第一条规则失败不会掩盖其余规则的违规；
 * 每条违规都带上所在类与修复建议；报告写到 {@code target/archguard/}，
 * AI 编码代理可以读取 {@code violations.json} 或 {@code violations.md} 后自行修正。
 * 报告目录可用系统属性 {@code ddk.archguard.report-dir} 覆盖。
 *
 * <pre>{@code
 * @Test
 * void architectureIsRespected() {
 *     ArchGuard.check(classes, CommonArchRules.LAYERED_ARCHITECTURE_RULE, CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS);
 * }
 * }</pre>
 */
public final class ArchGuard {

    public static final String REPORT_DIR_PROPERTY = "ddk.archguard.report-dir";

    private static final Path DEFAULT_REPORT_DIR = Path.of("target", "archguard");

    private static final Pattern CODE_UNIT = Pattern.compile("<([^<>]+)>");

    private ArchGuard() {
    }

    /**
     * 执行规则、写出报告；有违规时抛出 {@link AssertionError}，消息里列出每条违规与修复建议。
     */
    public static ArchGuardReport check(JavaClasses classes, ArchRule... rules) {
        return check(classes, reportDirectory(), rules);
    }

    public static ArchGuardReport check(JavaClasses classes, Path reportDirectory, ArchRule... rules) {
        ArchGuardReport report = evaluate(classes, rules);
        report.writeTo(reportDirectory);
        if (report.hasViolations()) {
            throw new AssertionError(failureMessage(report, reportDirectory));
        }
        return report;
    }

    public static ArchGuardReport evaluate(JavaClasses classes, ArchRule... rules) {
        List<Violation> violations = new ArrayList<>();
        for (ArchRule rule : rules) {
            EvaluationResult result = rule.evaluate(classes);
            if (!result.hasViolation()) {
                continue;
            }
            RuleHints.Hint hint = RuleHints.of(rule);
            for (String detail : result.getFailureReport().getDetails()) {
                violations.add(new Violation(hint.ruleId(), rule.getDescription(), owningClass(classes, detail), detail, hint.text()));
            }
        }
        return new ArchGuardReport(violations);
    }

    /**
     * ArchUnit 的违规描述形如 {@code Field <com.acme.domain.Order.state> has type <com.acme.infrastructure.OrderPO>}，
     * 第一个尖括号里是违规的代码单元：可能是类、字段或方法，逐级去掉成员部分直到命中导入的类。
     */
    static @Nullable String owningClass(JavaClasses classes, String detail) {
        Matcher matcher = CODE_UNIT.matcher(detail);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            int parenthesis = candidate.indexOf('(');
            if (parenthesis >= 0) {
                candidate = candidate.substring(0, parenthesis);
            }
            while (!candidate.isEmpty()) {
                if (classes.contain(candidate)) {
                    return candidate;
                }
                int dot = candidate.lastIndexOf('.');
                if (dot < 0) {
                    break;
                }
                candidate = candidate.substring(0, dot);
            }
        }
        return null;
    }

    private static Path reportDirectory() {
        String configured = System.getProperty(REPORT_DIR_PROPERTY);
        return configured == null || configured.isBlank() ? DEFAULT_REPORT_DIR : Path.of(configured);
    }

    private static String failureMessage(ArchGuardReport report, Path reportDirectory) {
        StringBuilder out = new StringBuilder("Architecture violated: ")
                .append(report.violations().size()).append(" violation(s). Fix the code; do not delete or relax the rules.\n")
                .append("Report: ").append(reportDirectory.resolve(ArchGuardReport.MARKDOWN_FILE)).append('\n');
        for (Violation v : report.violations()) {
            out.append("\n[").append(v.ruleId()).append("] ").append(v.className() == null ? "?" : v.className()).append('\n')
                    .append("  ").append(v.detail()).append('\n')
                    .append("  How to fix: ").append(v.hint()).append('\n');
        }
        return out.toString();
    }
}
