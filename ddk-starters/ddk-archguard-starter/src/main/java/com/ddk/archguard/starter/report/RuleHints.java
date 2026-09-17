package com.ddk.archguard.starter.report;

import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.lang.ArchRule;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * {@link CommonArchRules} 中每条规则的标识与修复建议。建议写成可以直接照做的动作，而不是复述规则本身。
 */
final class RuleHints {

    static final String CUSTOM_RULE_ID = "CUSTOM";

    private static final String CUSTOM_HINT = "按规则描述调整依赖；不要删除或放宽规则来让构建通过。";

    private static final Map<ArchRule, Hint> HINTS = new IdentityHashMap<>();

    static {
        HINTS.put(CommonArchRules.LAYERED_ARCHITECTURE_RULE, new Hint("LAYERED_ARCHITECTURE_RULE",
                "依赖方向应为 adapter → application → domain，infrastructure 只实现 domain.acl 中的接口。"
                        + "控制器里的仓储调用移到应用服务；应用服务与领域层对 *RepositoryImpl、*Mapper、*PO 的引用改为 domain.acl 中的接口。"));
        HINTS.put(CommonArchRules.THREE_LAYER_ARCHITECTURE_RULE, new Hint("THREE_LAYER_ARCHITECTURE_RULE",
                "依赖方向应为 adapter → business，infrastructure 只实现 business.acl 中的接口。"
                        + "业务层与适配层对 *RepositoryImpl、*Mapper、*PO 的引用改为 business.acl 中的接口。"));
        HINTS.put(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS, new Hint("DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS",
                "从领域层移除框架依赖：持久化注解放到 infrastructure 的 PO 上，Jackson 注解放到 *Request、*Response 上，"
                        + "需要的 Spring 能力在 domain.acl 定义端口，由 infrastructure 实现。"));
        HINTS.put(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS, new Hint("DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS",
                "领域层不得引用外层类型：需要的能力在 domain.acl 定义端口并由 infrastructure 实现，需要的数据以参数或值对象传入。"));
        HINTS.put(CommonArchRules.MCP_TOOLS_MUST_RESIDE_IN_ADAPTER, new Hint("MCP_TOOLS_MUST_RESIDE_IN_ADAPTER",
                "把 @McpTool 方法移到 adapter 包（例如 adapter.mcp）的工具类中，工具方法只调用应用服务，不直接使用仓储或领域服务。"));
        HINTS.put(CommonArchRules.DDK_INTERNALS_MUST_NOT_BE_USED, new Hint("DDK_INTERNALS_MUST_NOT_BE_USED",
                "改用 DDK 公开 API：覆盖对应的 Bean，或实现公开的扩展接口（如 CacheMetrics、CacheInvalidationPublisher），"
                        + "不要引用 com.ddk..internal.. 中的类型。"));
    }

    private RuleHints() {
    }

    static Hint of(ArchRule rule) {
        return HINTS.getOrDefault(rule, new Hint(CUSTOM_RULE_ID, CUSTOM_HINT));
    }

    record Hint(
            String ruleId,

            String text
    ) {
    }
}
