package com.ddk.archguard.starter.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一次架构检查的全部违规，可以输出为 JSON（给工具与 AI 代理解析）和 Markdown（给人和代理阅读）。
 *
 * @param violations 违规列表，按规则分组、保持发现顺序
 */
public record ArchGuardReport(
        List<Violation> violations
) {

    public static final String JSON_FILE = "violations.json";

    public static final String MARKDOWN_FILE = "violations.md";

    public ArchGuardReport {
        violations = List.copyOf(violations);
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    public void writeTo(Path directory) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(JSON_FILE), toJson(), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(MARKDOWN_FILE), toMarkdown(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write ArchGuard report to " + directory, e);
        }
    }

    public String toJson() {
        String items = violations.stream()
                .map(v -> "    {\n"
                        + "      \"rule\": " + quote(v.ruleId()) + ",\n"
                        + "      \"class\": " + (v.className() == null ? "null" : quote(v.className())) + ",\n"
                        + "      \"detail\": " + quote(v.detail()) + ",\n"
                        + "      \"hint\": " + quote(v.hint()) + ",\n"
                        + "      \"ruleDescription\": " + quote(v.ruleDescription()) + "\n"
                        + "    }")
                .collect(Collectors.joining(",\n"));
        return "{\n  \"violationCount\": " + violations.size() + ",\n  \"violations\": [" + (items.isEmpty() ? "" : "\n" + items + "\n  ") + "]\n}\n";
    }

    public String toMarkdown() {
        StringBuilder out = new StringBuilder("# ArchGuard report\n\n");
        if (violations.isEmpty()) {
            return out.append("No architecture violations.\n").toString();
        }
        out.append(violations.size()).append(" violation(s). Fix the code; do not delete or relax the rules.\n");
        Map<String, List<Violation>> byRule = violations.stream()
                .collect(Collectors.groupingBy(Violation::ruleId, LinkedHashMap::new, Collectors.toList()));
        byRule.forEach((ruleId, items) -> {
            out.append("\n## ").append(ruleId).append("\n\n");
            out.append("**Rule**: ").append(items.get(0).ruleDescription()).append("\n\n");
            out.append("**How to fix**: ").append(items.get(0).hint()).append("\n\n");
            items.forEach(v -> out.append("- `").append(v.className() == null ? "?" : v.className()).append("`: ")
                    .append(v.detail()).append('\n'));
        });
        return out.toString();
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
