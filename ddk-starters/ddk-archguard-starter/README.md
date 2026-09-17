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
    void layeredArchitectureIsRespected() {
        CommonArchRules.LAYERED_ARCHITECTURE_RULE.check(classes);
    }

    @Test
    void domainStaysFrameworkFree() {
        CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
    }
}
```

- `DO_NOT_INCLUDE_JARS` keeps library classes whose packages happen to match a layer, such as `com.ddk.core.domain`, out of the analysis.
- `DO_NOT_INCLUDE_TESTS` skips test classes, which often reach across layers to set up scenarios.

## Rules

| Rule | Checks |
|---|---|
| `LAYERED_ARCHITECTURE_RULE` | Four layers: `..adapter..`/`..ui..` → `..application..` → `..domain..`; `..infrastructure..` only implements domain ports |
| `THREE_LAYER_ARCHITECTURE_RULE` | Three layers: `..adapter..` → `..business..`; `..infrastructure..` implements business interfaces and is referenced by no other layer |
| `DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS` | `..domain..` does not use Spring, MyBatis(-Plus), Jackson or JPA |
| `DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS` | `..domain..` does not depend on application, adapter or infrastructure packages |

```text
four layers                               three layers
  adapter ──► application ──► domain        adapter ──► business
                   │            ▲                          ▲
                   └──► infrastructure                infrastructure
```

Every rule passes on projects where a layer has no classes yet, so a freshly generated skeleton builds without placeholder code.

## Custom package names

Layers are matched by package name. If your packages differ, copy the rule from `CommonArchRules` and change its `definedBy(...)` patterns.
