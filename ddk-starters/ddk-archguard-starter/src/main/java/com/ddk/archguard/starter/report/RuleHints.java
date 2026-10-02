package com.ddk.archguard.starter.report;

import com.ddk.archguard.starter.rules.CommonArchRules;
import com.tngtech.archunit.lang.ArchRule;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@link CommonArchRules} 中每条规则的标识与修复建议。建议写成可以直接照做的动作，而不是复述规则本身。
 * <p>
 * 建议有中文和英文两份。语言取系统属性 {@value #LANGUAGE_PROPERTY}（{@code zh} 或 {@code en}）；没有设置时跟随 JVM 的默认语言，
 * 中文环境用中文，其余用英文。
 */
final class RuleHints {

    static final String LANGUAGE_PROPERTY = "ddk.archguard.language";

    static final String CUSTOM_RULE_ID = "CUSTOM";

    private static final Hint CUSTOM_HINT = new Hint(CUSTOM_RULE_ID,
            "按规则描述调整依赖；不要删除或放宽规则来让构建通过。",
            "Change the dependencies as the rule describes. Do not delete or relax the rule to make the build pass.");

    private static final Map<ArchRule, Hint> HINTS = new IdentityHashMap<>();

    static {
        HINTS.put(CommonArchRules.LAYERED_ARCHITECTURE_RULE, new Hint("LAYERED_ARCHITECTURE_RULE",
                "依赖方向应为 adapter → application → domain，infrastructure 只实现 domain.acl 中的接口。"
                        + "控制器里的仓储调用移到应用服务；应用服务与领域层对 *RepositoryImpl、*Mapper、*PO 的引用改为 domain.acl 中的接口。",
                "Dependencies point adapter → application → domain, and infrastructure only implements the interfaces in domain.acl. "
                        + "Move repository calls out of controllers into application services; in the application and domain layers, "
                        + "replace references to *RepositoryImpl, *Mapper and *PO with the interfaces in domain.acl."));
        HINTS.put(CommonArchRules.THREE_LAYER_ARCHITECTURE_RULE, new Hint("THREE_LAYER_ARCHITECTURE_RULE",
                "依赖方向应为 adapter → business，infrastructure 只实现 business.acl 中的接口。"
                        + "业务层与适配层对 *RepositoryImpl、*Mapper、*PO 的引用改为 business.acl 中的接口。",
                "Dependencies point adapter → business, and infrastructure only implements the interfaces in business.acl. "
                        + "In the business and adapter layers, replace references to *RepositoryImpl, *Mapper and *PO "
                        + "with the interfaces in business.acl."));
        HINTS.put(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS, new Hint("DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS",
                "从领域层移除框架依赖：持久化注解放到 infrastructure 的 PO 上，Jackson 注解放到 *Request、*Response 上，"
                        + "需要的 Spring 能力在 domain.acl 定义端口，由 infrastructure 实现。",
                "Remove the framework dependency from the domain layer: put persistence annotations on the PO in infrastructure and "
                        + "Jackson annotations on *Request and *Response; for a Spring capability, define a port in domain.acl "
                        + "and implement it in infrastructure."));
        HINTS.put(CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS, new Hint("DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS",
                "领域层不得引用外层类型：需要的能力在 domain.acl 定义端口并由 infrastructure 实现，需要的数据以参数或值对象传入。",
                "The domain layer must not reference types from outer layers: define a port in domain.acl for the capability and "
                        + "implement it in infrastructure, and pass the data in as parameters or value objects."));
        HINTS.put(CommonArchRules.MCP_TOOLS_MUST_RESIDE_IN_ADAPTER, new Hint("MCP_TOOLS_MUST_RESIDE_IN_ADAPTER",
                "把 @McpTool 方法移到 adapter 包（例如 adapter.mcp）的工具类中，工具方法只调用应用服务，不直接使用仓储或领域服务。",
                "Move the @McpTool method to a tool class in the adapter package (for example adapter.mcp). A tool method only calls "
                        + "application services, never repositories or domain services."));
        HINTS.put(CommonArchRules.SCHEDULED_JOBS_MUST_RESIDE_IN_ADAPTER, new Hint("SCHEDULED_JOBS_MUST_RESIDE_IN_ADAPTER",
                "把 @Scheduled / @XxlJob 方法移到 adapter 包（例如 adapter.job）的任务类中，任务方法只调用应用服务，不直接使用仓储、Mapper 或领域服务。",
                "Move the @Scheduled / @XxlJob method to a job class in the adapter package (for example adapter.job). A job method "
                        + "only calls application services, never repositories, mappers or domain services."));
        HINTS.put(CommonArchRules.SCHEDULED_JOBS_MUST_BE_LOCKED, new Hint("SCHEDULED_JOBS_MUST_BE_LOCKED",
                "给 @Scheduled 方法加上 @SchedulerLock(name = \"唯一的任务名\")，让多实例下同一轮只有一个实例执行。",
                "Add @SchedulerLock(name = \"a unique job name\") to the @Scheduled method, so only one instance runs each round."));
        HINTS.put(CommonArchRules.DDK_INTERNALS_MUST_NOT_BE_USED, new Hint("DDK_INTERNALS_MUST_NOT_BE_USED",
                "改用 DDK 公开 API：覆盖对应的 Bean，或实现公开的扩展接口（如 CacheMetrics、CacheInvalidationPublisher），"
                        + "不要引用 com.ddk..internal.. 中的类型。",
                "Use DDK's public API instead: override the bean, or implement a public extension interface such as CacheMetrics or "
                        + "CacheInvalidationPublisher. Do not reference types in com.ddk..internal.."));
    }

    private RuleHints() {
    }

    static Hint of(ArchRule rule) {
        return HINTS.getOrDefault(rule, CUSTOM_HINT);
    }

    static boolean chinese() {
        String configured = System.getProperty(LANGUAGE_PROPERTY);
        String language = configured == null || configured.isBlank() ? Locale.getDefault().getLanguage() : configured.trim();
        return language.toLowerCase(Locale.ROOT).startsWith("zh");
    }

    record Hint(
            String ruleId,

            String chinese,

            String english
    ) {

        String text() {
            return RuleHints.chinese() ? chinese : english;
        }
    }
}
