# DDK ArchGuard Starter

Executable architecture rules for DDD projects, built on ArchUnit. Layering written in a document is a suggestion; written as a test it fails the build.

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-archguard-starter</artifactId>
    <scope>test</scope>
</dependency>
```

Always use `test` scope: the rules expose ArchUnit types, so ArchUnit is a compile dependency of this module.

```java
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.app");

    @Test
    void architectureIsRespected() {
        ArchGuard.check(classes,
                CommonArchRules.LAYERED_ARCHITECTURE_RULE,
                CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS,
                CommonArchRules.DDK_INTERNALS_MUST_NOT_BE_USED);
    }
}
```

Each rule can still be checked on its own with `rule.check(classes)`.

- `DO_NOT_INCLUDE_JARS` keeps library classes whose packages happen to match a layer, such as `com.ddk.core.domain`, out of the analysis.
- `DO_NOT_INCLUDE_TESTS` skips test classes, which often reach across layers to set up scenarios.

## Violation reports

`ArchGuard.check` evaluates every rule instead of stopping at the first failure, then writes a report to `target/archguard/` (override with `-Dddk.archguard.report-dir=...`):

| File | For |
|---|---|
| `violations.md` | People and AI coding agents: violations grouped by rule, each with a concrete fix |
| `violations.json` | Tools and agents that parse the result: `rule`, `class`, `detail`, `hint`, `ruleDescription` per violation |

The assertion message carries the same content, so a failing build log is enough to act on:

```text
Architecture violated: 2 violation(s). Fix the code; do not delete or relax the rules.
Report: target/archguard/violations.md

[LAYERED_ARCHITECTURE_RULE] com.acme.order.domain.model.Order
  Field <com.acme.order.domain.model.Order.state> has type <com.acme.order.infrastructure.orm.po.OrderPO> in (Order.java:0)
  How to fix: Dependencies point adapter → application → domain, and infrastructure only implements the interfaces in domain.acl. ...

[DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS] com.acme.order.domain.model.Order
  Field <com.acme.order.domain.model.Order.state> has type <com.acme.order.infrastructure.orm.po.OrderPO> in (Order.java:0)
  How to fix: The domain layer must not reference types from outer layers: define a port in domain.acl for the capability and implement it in infrastructure, and pass the data in as parameters or value objects.
```

Rules outside `CommonArchRules` are reported with the rule ID `CUSTOM` and a generic hint.

Hints come in English and Chinese. The language follows the JVM's default locale: Chinese on a Chinese locale, English otherwise. To force one, set the system property `ddk.archguard.language` to `en` or `zh`, for example `mvn verify -DargLine="-Dddk.archguard.language=zh"`.

## Rules

| Rule | Checks |
|---|---|
| `LAYERED_ARCHITECTURE_RULE` | Four layers: `..adapter..`/`..ui..` → `..application..` → `..domain..`; `..infrastructure..` only implements domain ports |
| `THREE_LAYER_ARCHITECTURE_RULE` | Three layers: `..adapter..` → `..business..`; `..infrastructure..` implements business interfaces and is referenced by no other layer |
| `DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS` | `..domain..` does not use Spring, MyBatis(-Plus), Jackson or JPA |
| `DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS` | `..domain..` does not depend on application, adapter or infrastructure packages |
| `DDK_INTERNALS_MUST_NOT_BE_USED` | Application code does not depend on `com.ddk..internal..`, which holds starter implementation details that may change in any release |
| `SCHEDULED_JOBS_MUST_RESIDE_IN_ADAPTER` | `@Scheduled` and `@XxlJob` methods are declared in `..adapter..` classes, so a job is one more entry point that goes through application services |
| `SCHEDULED_JOBS_MUST_BE_LOCKED` | Optional, for projects using ShedLock: every `@Scheduled` method also carries `@SchedulerLock`, so no job silently runs on every instance |
| `MCP_TOOLS_MUST_RESIDE_IN_ADAPTER` | `@McpTool` methods are declared in `..adapter..` classes, so tools reach the domain only through application services |

```text
four layers                               three layers
  adapter ──► application ──► domain        adapter ──► business
                   │            ▲                          ▲
                   └──► infrastructure                infrastructure
```

Every rule passes on projects where a layer has no classes yet, so a freshly generated skeleton builds without placeholder code.

## Custom package names

Layers are matched by package name. If your packages differ, copy the rule from `CommonArchRules` and change its `definedBy(...)` patterns.
