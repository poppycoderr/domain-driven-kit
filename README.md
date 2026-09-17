<p align="center">
    <img src="./assets/brand/logo.svg" alt="Domain Driven Kit" width="140" />
</p>

<h1 align="center">Domain Driven Kit</h1>

<p align="center">
    <b>Executable DDD for Spring Boot.</b><br/>Domain building blocks out of the box, architecture rules enforced in CI, and the same guardrails for humans and AI coding agents.
</p>

<p align="center">
    <a href="https://github.com/poppycoderr/domain-driven-kit/actions/workflows/build.yml"><img src="https://github.com/poppycoderr/domain-driven-kit/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
    <a href="./LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License" /></a>
    <img src="https://img.shields.io/badge/Java-21%20%7C%2025-ED8B00?logo=openjdk&logoColor=white" alt="Java 21 | 25" />
    <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4.1" />
    <a href="https://poppycoder.netlify.app/ddk/"><img src="https://img.shields.io/badge/docs-codesphere-06B6D4" alt="Docs" /></a>
</p>

<p align="center">
    <b>English</b> · <a href="./README.zh-CN.md">简体中文</a> · <a href="https://poppycoder.netlify.app/ddk/">Documentation</a> · <a href="./ROADMAP.md">Roadmap</a>
</p>

---

## Highlights

- 🧱 **Domain building blocks**: `Identifier`, `ValueObject`, `Entity`, `AggregateRoot`, domain events and specifications, with zero framework dependencies enforced by tests
- 🛡️ **Architecture rules that fail the build**: ArchUnit rules for three- and four-layer projects, included in every generated project
- 🗄️ **Safe persistence**: a generic MyBatis-Plus repository with optimistic locking; missing or duplicate mappers fail at startup
- ⚡ **Two-level cache**: Caffeine + Redis with cross-instance invalidation, Redis failure fallback and a deserialization allow-list
- 🧩 **Nine Spring Boot starters** under one `ddk.*` namespace: web, MyBatis, Redis, cache, data sources, tracing, Seata, domain events, ArchGuard
- 🚀 **Start in minutes**: Maven archetypes and a runnable example, built and tested in CI on Java 21 and 25

## Why DDK

`domain-driven-kit` is not a heavy business framework. It is an evolving **Java DDD engineering toolkit** that turns layered architecture, response contracts, exception handling, pagination, object mapping, repository abstractions, Spring Boot starters and architecture rules into reusable code.

<p align="center">
    <img src="./assets/diagrams/ddk-system-overview.svg" alt="DDK system overview" />
</p>

It is designed for teams that want to:

- Start a Java project with a clear DDD / layered architecture baseline
- Keep Controller, Application, Domain and Infrastructure responsibilities explicit
- Standardize repeated backend concerns such as API responses, exceptions, pagination and repository boundaries
- Move architecture rules into tests and CI with ArchUnit instead of leaving them only in documentation

## Quick Start

Requirements:

- JDK 21
- Maven 3.9+
- Spring Boot 4.1.x (Jackson 3)

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

See the full guide: [Quick Start](https://poppycoder.netlify.app/ddk/quickstart).

## Modules

DDK is maintained by one person and is still pre-release: build it locally, try the archetypes and use it as a reference. Releases are published on GitHub rather than Maven Central for now.

| Module | Capability | Status |
|---|---|---|
| `ddk-core` | Domain model primitives, `ApiResponse`, exceptions, pagination, mapper registry, repository contract | Usable |
| `ddk-mybatis` | MyBatis-Plus repository implementation, query parsing, pagination, optimistic locking, domain event publishing | Usable, with H2 integration tests |
| `ddk-web-starter` | Jackson, CORS, global exception handling, configurable under `ddk.web.*` | Usable |
| `ddk-mybatis-starter` | Pagination, optimistic locking, full-table update/delete guard, snowflake IDs, configurable under `ddk.mybatis.*` | Usable |
| `ddk-event-starter` | Spring-backed domain event publisher | Usable |
| `ddk-redis-starter` | JSON `RedisTemplate` with a deserialization type allow-list | Usable |
| `ddk-cache-starter` | Caffeine (L1) + Redis (L2) two-level cache with cross-instance invalidation and Redis failure fallback | Usable, with Redis integration tests |
| `ddk-archguard-starter` | ArchUnit rules for layering and domain purity | Usable |
| `ddk-dependencies` | BOM, so downstream projects stop writing versions | Usable |
| `ddk-db-starter` | Named data sources with configurable pools and a validated primary | Usable |
| `ddk-tracer-starter` | Distributed tracing with the trace ID in a response header | Usable |
| `ddk-seata-starter` | Distributed transactions with XID propagation on outbound HTTP calls | Usable |
| `ddk-archetypes` | Three- and four-layer Maven archetypes with architecture tests in every generated project | Usable, with integration tests on generated projects |
| `ddk-examples` | Runnable four-layer user registration example with H2, seed data and smoke commands | Usable |

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

## Architecture Guard

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

A violation fails the build. See [`ddk-examples/ddk-example-user`](./ddk-examples/ddk-example-user) for a working example.

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

## Roadmap

<p align="center">
    <img src="./assets/diagrams/ddk-roadmap.svg" alt="DDK roadmap" />
</p>

The foundation (domain model, repositories, nine starters, archetypes and examples) is complete. Next milestones:

1. **v0.1 Modern baseline and first release**: Spring Boot 4.1 / Jackson 3 and the first GitHub Release
2. **v0.2 AI collaboration**: generated projects ship `AGENTS.md` and Skills, ArchGuard reports violations agents can act on, an MCP starter on Spring AI
3. **v0.3 Reliable domain events**: transactional outbox, RocketMQ / Kafka delivery, idempotent consumers
4. **v0.4 Middleware integrations**: Redisson aggregate locks, XXL-Job, Flyway, springdoc, Elasticsearch read models
5. **v1.0 Production ready**: the `ddk-mall` reference application, native images, a frozen public API

See [ROADMAP.md](./ROADMAP.md) for the full plan.

## Documentation

- [DDK documentation](https://poppycoder.netlify.app/ddk/)
- [Quick Start](https://poppycoder.netlify.app/ddk/quickstart)
- [Domain Model Primitives](https://poppycoder.netlify.app/ddk/core/domain-model)
- [Layering and Architecture Guard](https://poppycoder.netlify.app/ddk/conventions)
- [Development and Refactoring Plan](https://poppycoder.netlify.app/ddk/contributing)

## Contributing

Issues and PRs are welcome. The most useful contributions right now are:

- Starter auto-configuration fixes
- Tests and runnable examples
- DDD layering examples and documentation
- Clearer trade-off analysis for existing design decisions

Run `mvn verify` before opening a PR: it checks formatting with Spotless (fix with `mvn spotless:apply`) and requires at least 70% line and 50% branch coverage per module.

## Built With

<p>
    <a href="https://www.jetbrains.com/idea/"><img src="https://img.shields.io/badge/IntelliJ%20IDEA-000000?logo=intellijidea&logoColor=white" alt="IntelliJ IDEA" /></a>
    <a href="https://claude.com/claude-code"><img src="https://img.shields.io/badge/Claude%20Code-D97757?logo=claude&logoColor=white" alt="Claude Code" /></a>
    <a href="https://openai.com/codex"><img src="https://img.shields.io/badge/Codex-111111" alt="Codex" /></a>
    <a href="https://github.com/features/actions"><img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?logo=githubactions&logoColor=white" alt="GitHub Actions" /></a>
    <a href="https://vitepress.dev"><img src="https://img.shields.io/badge/VitePress-646CFF?logo=vitepress&logoColor=white" alt="VitePress" /></a>
    <a href="https://docsify.js.org"><img src="https://img.shields.io/badge/docsify-42B983?logo=docsify&logoColor=white" alt="docsify" /></a>
</p>

- Code and docs are written in IntelliJ IDEA with the help of Claude Code and Codex; every change is reviewed, tested and passes CI before it is merged.
- The documentation site [codesphere](https://poppycoder.netlify.app) is built with VitePress; earlier versions used docsify.

## License

[Apache License 2.0](./LICENSE). Free to use, modify and distribute, including commercially; keep the copyright and license notices, and state changes made to modified files.
