<p align="center">
    <img src="./logo.png" alt="ddk logo" width="220" />
</p>

<h1 align="center">Domain Driven Kit</h1>

<p align="center">
    A Java / Spring Boot toolkit for practical DDD scaffolding and engineering conventions.
</p>

<p align="center">
    <a href="README.md">中文</a> ·
    <a href="https://poppycoder.netlify.app/#/docs/ddk/">Documentation</a> ·
    <a href="./ROADMAP.md">Roadmap</a>
</p>

---

## What This Project Is

`domain-driven-kit` is not a heavy business framework. It is an evolving **Java DDD engineering toolkit** that turns layered architecture, response contracts, exception handling, pagination, object mapping, repository abstractions, Spring Boot starters and architecture rules into reusable code.

It is designed for teams that want to:

- Start a Java project with a clear DDD / layered architecture baseline
- Keep Controller, Application, Domain and Infrastructure responsibilities explicit
- Standardize repeated backend concerns such as API responses, exceptions, pagination and repository boundaries
- Move architecture rules into tests and CI with ArchUnit instead of leaving them only in documentation

## Current Status

This is a personally maintained open-source project. It is useful for learning, local experiments and as a reference implementation, but it has not been published to Maven Central yet.

| Module | Capability | Status |
|---|---|---|
| `ddk-core` | `ApiResponse`, exceptions, pagination, mapper abstractions, repository contract | Usable, tests being expanded |
| `ddk-mybatis` | MyBatis-Plus repository implementation, query parsing, pagination adapter | Usable, page sorting has been fixed |
| `ddk-web-starter` | Jackson, CORS, global exception handling | Usable, global exception advice is now active |
| `ddk-archguard-starter` | DDD layered architecture rules for ArchUnit | Usable, Domain -> Infrastructure dependency has been tightened |
| `ddk-db-starter` | Dynamic multi-data-source registration | Experimental |
| `ddk-cache-starter` | Cache starter draft | Planned rewrite |
| `ddk-archetypes` | 3-layer / 4-layer project skeletons | To be converted into real Maven archetypes |
| `ddk-examples` | Example project module | Full runnable example planned |

## Recent Updates

- Fixed `BaseExceptionHandler` so it is registered as a global `@RestControllerAdvice`
- Added a default max page size to `PageQuery`
- Fixed `PageQuery.addSort()` so sorting reaches the MyBatis-Plus query wrapper
- Tightened `CommonArchRules` to prevent Domain from depending on Infrastructure
- Reworked the README and roadmap to make the current state and future work explicit

## Module Layout

```text
domain-driven-kit
├── ddk-dependencies      Dependency management module, planned as a real BOM
├── ddk-core              Core abstractions: exception, response, pagination, mapper, repository contract
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

Use in another project:

```xml
<properties>
    <ddk.version>1.0.0-SNAPSHOT</ddk.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-core</artifactId>
        <version>${ddk.version}</version>
    </dependency>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-mybatis</artifactId>
        <version>${ddk.version}</version>
    </dependency>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-web-starter</artifactId>
        <version>${ddk.version}</version>
    </dependency>
</dependencies>
```

See the full guide: [Quick Start](https://poppycoder.netlify.app/#/docs/ddk/quickstart).

## Recommended Layering

```text
adapter          -> application -> domain
infrastructure  --------------------^
```

Core constraints:

- `adapter` adapts external protocols and should not contain business rules
- `application` orchestrates use cases, transactions and domain objects
- `domain` owns business rules and should not depend on Spring, MyBatis, Jackson or other frameworks
- `infrastructure` implements repository and external dependency contracts defined by the domain layer

`ddk-archguard-starter` provides ArchUnit rules to make these boundaries executable in tests.

## Roadmap

Short-term priorities:

1. Add focused tests for `ddk-core` and `ddk-mybatis`
2. Turn `ddk-dependencies` into a real BOM
3. Implement domain model primitives: `Entity`, `ValueObject`, `AggregateRoot`, `DomainEvent`
4. Normalize starter configuration prefixes, metadata and auto-configuration tests
5. Add a complete runnable `ddk-examples` application

See [ROADMAP.md](./ROADMAP.md) for the full plan.

## Documentation

- [DDK documentation](https://poppycoder.netlify.app/#/docs/ddk/)
- [Quick Start](https://poppycoder.netlify.app/#/docs/ddk/quickstart)
- [Layering and Architecture Guard](https://poppycoder.netlify.app/#/docs/ddk/conventions)
- [Development and Refactoring Plan](https://poppycoder.netlify.app/#/docs/ddk/contributing)

## Contributing

Issues and PRs are welcome. The most useful contributions right now are:

- Starter auto-configuration fixes
- Tests and runnable examples
- DDD layering examples and documentation
- Clearer trade-off analysis for existing design decisions
