<p align="center">
    <img src="./assets/brand/logo.svg" alt="Domain Driven Kit" width="140" />
</p>

<h1 align="center">Domain Driven Kit</h1>

<p align="center">
    A Java / Spring Boot toolkit for practical DDD scaffolding and engineering conventions.
</p>

<p align="center">
    <a href="README.md">中文</a> ·
    <a href="https://poppycoder.netlify.app/#/docs/ddk/index.md">Documentation</a> ·
    <a href="./ROADMAP.md">Roadmap</a>
</p>

---

## What This Project Is

`domain-driven-kit` is not a heavy business framework. It is an evolving **Java DDD engineering toolkit** that turns layered architecture, response contracts, exception handling, pagination, object mapping, repository abstractions, Spring Boot starters and architecture rules into reusable code.

<p align="center">
    <img src="./assets/diagrams/ddk-system-overview.svg" alt="DDK system overview" />
</p>

It is designed for teams that want to:

- Start a Java project with a clear DDD / layered architecture baseline
- Keep Controller, Application, Domain and Infrastructure responsibilities explicit
- Standardize repeated backend concerns such as API responses, exceptions, pagination and repository boundaries
- Move architecture rules into tests and CI with ArchUnit instead of leaving them only in documentation

## Current Status

This is a personally maintained open-source project. It is useful for learning, local experiments and as a reference implementation, but it has not been published to Maven Central yet.

| Module | Capability | Status |
|---|---|---|
| `ddk-core` | Domain model primitives, `ApiResponse`, exceptions, pagination, mapper registry, repository contract | Usable, 67 unit tests |
| `ddk-mybatis` | MyBatis-Plus repository implementation, query parsing, pagination adapter | Usable, 12 unit tests |
| `ddk-web-starter` | Jackson, CORS, global exception handling | Usable |
| `ddk-archguard-starter` | ArchUnit rules for layering and domain purity | Usable |
| `ddk-dependencies` | BOM, so downstream projects stop writing versions | Usable |
| `ddk-db-starter` | Dynamic multi-data-source registration | Experimental |
| `ddk-tracer-starter` / `ddk-seata-starter` | Distributed tracing / transactions | Experimental |
| `ddk-cache-starter` | Cache starter draft | Planned rewrite, see the [design walkthrough](https://poppycoder.netlify.app/#/docs/ddk/starters/cache-design.md) |
| `ddk-archetypes` | The 4-layer skeleton is readable and tested; the 3-layer one is still a stub | To be converted into real Maven archetypes |
| `ddk-examples` | Example project module | Full runnable example planned |

## Domain Model

<p align="center">
    <img src="./assets/diagrams/ddk-domain-model.svg" alt="DDK domain model base classes" />
</p>

`com.ddk.core.domain` does exactly three things:

- **Gives identity a type.** `Identifier` makes `UserId(1)` different from `OrderId(1)`, so swapped arguments fail at compile time
- **Separates value equality from identity equality.** `ValueObject` (an empty interface, so a `record` can implement it) and `Entity`
- **Gives domain events a place to be collected and published.** `AggregateRoot` registers them, `DomainEventPublisher` publishes them after commit

The package depends on no framework at all — not Spring, MyBatis or Jackson. That constraint is enforced by `DomainPackagePurityTest`, not by a note in the docs.

The primitives provide mechanism without dictating process, and you can adopt only the part you need.

## Recent Updates

- Shipped `com.ddk.core.domain`: `Identifier`, `ValueObject`, `Entity`, `AggregateRoot`, `DomainEvent`, `Specification`
- `MapperProvider` now keys mappers by fully qualified name, fixing silent overwrites between same-named classes in different packages
- A missing mapper now throws `MissingMapperException` instead of falling back to `DefaultMapper`, which mapped everything to an empty object
- `GenericRepository<ID, E>` became `<E, ID>`, matching its javadoc and Spring Data conventions (breaking change)
- The 4-layer archetype was rewritten from a broken anemic sample into a readable, tested one
- ArchGuard gained domain purity rules; the test count went from 14 to 96

## Module Layout

<p align="center">
    <img src="./assets/diagrams/ddk-module-map.svg" alt="DDK module map" />
</p>

```text
domain-driven-kit
├── ddk-dependencies      BOM, so downstream projects stop writing versions
├── ddk-core              Core abstractions: domain model, exception, response, pagination, mapper, repository contract
├── ddk-mybatis           MyBatis-Plus repository implementation and query adapters
├── ddk-starters          Spring Boot starter modules
│   ├── ddk-web-starter
│   ├── ddk-mybatis-starter
│   ├── ddk-redis-starter
│   ├── ddk-cache-starter
│   ├── ddk-db-starter
│   ├── ddk-tracer-starter
│   ├── ddk-seata-starter
│   └── ddk-archguard-starter
├── ddk-archetypes        3-layer / 4-layer project skeletons
└── ddk-examples          Example applications
```

Inside `ddk-core`:

```text
com.ddk.core
├── domain        Domain model primitives, zero framework dependencies
├── repository    The GenericRepository contract
├── page          PageQuery / PageResponse / Sort
├── mapper        MapperProvider and @EnhancedMapper
├── response      ApiResponse
└── exception     ErrorCode and the exception hierarchy
```

## Quick Start

Requirements:

- JDK 21
- Maven 3.9+
- Spring Boot 3.4.x

Build locally:

```bash
git clone https://github.com/poppycoderr/domain-driven-kit.git
cd domain-driven-kit
mvn -B -ntp verify
mvn -B install
```

Use in another project. Import the BOM first, then drop the versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.ddk</groupId>
            <artifactId>ddk-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-web-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-mybatis-starter</artifactId>
    </dependency>

    <!-- Architecture rules belong on the test classpath only -->
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-archguard-starter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

See the full guide: [Quick Start](https://poppycoder.netlify.app/#/docs/ddk/quickstart.md).

## Recommended Layering

<p align="center">
    <img src="./assets/diagrams/ddk-layer-flow.svg" alt="DDK four-layer request flow" />
</p>

Core constraints:

- `adapter` adapts external protocols and should not contain business rules
- `application` orchestrates use cases, transactions and domain objects
- `domain` owns business rules and should not depend on Spring, MyBatis, Jackson or other frameworks
- `infrastructure` implements repository and external dependency contracts defined by the domain layer

Written in a document these are suggestions; written as a test they are constraints:

```java
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.myapp");

    @Test
    void layered_architecture_is_respected() {
        CommonArchRules.LAYERED_ARCHITECTURE_RULE.check(classes);
    }

    @Test
    void domain_stays_framework_free() {
        CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
    }
}
```

A violation fails the build. See `ddk-archetypes/ddk-layer4-archetype` for a working example.

## Roadmap

<p align="center">
    <img src="./assets/diagrams/ddk-roadmap.svg" alt="DDK roadmap" />
</p>

Short-term priorities:

1. Normalize starter configuration prefixes, metadata and auto-configuration tests
2. Rewrite `ddk-cache-starter` following the [design walkthrough](https://poppycoder.netlify.app/#/docs/ddk/starters/cache-design.md)
3. Turn `ddk-archetypes` into real Maven archetypes
4. Add a complete runnable `ddk-examples` application
5. Add H2-backed integration tests for `ddk-mybatis`

See [ROADMAP.md](./ROADMAP.md) for the full plan.

## Documentation

- [DDK documentation](https://poppycoder.netlify.app/#/docs/ddk/index.md)
- [Quick Start](https://poppycoder.netlify.app/#/docs/ddk/quickstart.md)
- [Domain Model Primitives](https://poppycoder.netlify.app/#/docs/ddk/core/domain-model.md)
- [Layering and Architecture Guard](https://poppycoder.netlify.app/#/docs/ddk/conventions.md)
- [Development and Refactoring Plan](https://poppycoder.netlify.app/#/docs/ddk/contributing.md)

## Contributing

Issues and PRs are welcome. The most useful contributions right now are:

- Starter auto-configuration fixes
- Tests and runnable examples
- DDD layering examples and documentation
- Clearer trade-off analysis for existing design decisions
