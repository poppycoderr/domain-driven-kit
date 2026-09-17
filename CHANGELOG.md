# Changelog

All notable changes to Domain Driven Kit are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

DDK follows [Semantic Versioning](https://semver.org/) with the usual 0.x caveat:

- **0.x**: a minor release (`0.1` → `0.2`) may contain breaking changes, and every one is listed under **Breaking** below. Patch releases (`0.1.0` → `0.1.1`) never break.
- **1.0 onwards**: breaking changes only in major releases.
- Releases are tagged `vX.Y.Z` and published as [GitHub Releases](https://github.com/poppycoderr/domain-driven-kit/releases). DDK is not on Maven Central yet; install it locally with [`scripts/ddk.sh`](./scripts/ddk.sh).

## [Unreleased]

### Added

- Generated projects ship `AGENTS.md` and `CLAUDE.md` describing layers, conventions and the `mvn verify` gate for AI coding agents
- Claude Code Skills in generated projects: `ddk-add-aggregate`, `ddk-add-use-case` and `ddk-add-domain-event` for the four-layer template, `ddk-add-feature` for the three-layer template
- Generated projects include `spring-boot-starter-webmvc-test` for MockMvc tests
- `AGENTS.md` and `CLAUDE.md` for contributors to DDK itself
- `ddk-mcp-starter`: exposes application use cases as MCP tools on top of Spring AI 2.0, with streamable HTTP by default, Bean Validation on tool arguments, error-coded tool errors, hidden internals for unexpected exceptions and an audit log line per call
- `CommonArchRules.MCP_TOOLS_MUST_RESIDE_IN_ADAPTER` keeps `@McpTool` methods in the adapter layer
- The user example exposes `register_user`, `get_user` and `disable_user` as MCP tools
- `ArchGuard.check` evaluates all architecture rules at once and writes `target/archguard/violations.json` and `violations.md`, each violation with its rule, class and a concrete fix; generated projects and the example use it

### Fixed

- `scripts/ddk.sh` installs `X.Y.Z` instead of the snapshot version when `DDK_REF` names a release tag such as `v0.1.0`

## [0.1.0] - 2026-09-17

The first release: the DDD foundation on a Spring Boot 4.1 baseline.

### Added

- Domain model primitives in `com.ddk.core.domain`: `Identifier`, `ValueObject`, `Entity`, `AggregateRoot`, `DomainEvent`, `DomainEventPublisher`, `Specification`, with a framework-free domain package enforced by tests
- `GenericRepository` backed by MyBatis-Plus, with Entity ↔ PO mappers that fail at startup when missing or duplicated, and optimistic locking through `AggregateRoot.version()`
- Starters under a unified `ddk.*` namespace: web, MyBatis, domain events, Redis, two-level cache, named data sources, tracing, Seata and ArchGuard
- `ddk-cache-starter`: Caffeine (L1) + Redis (L2) with cross-instance invalidation, Redis failure fallback, TTL jitter and Micrometer metrics
- `ddk-redis-starter`: a JSON `RedisTemplate` whose deserialization only accepts allow-listed packages
- `ddk-archguard-starter`: three- and four-layer ArchUnit rules, domain purity rules, and `DDK_INTERNALS_MUST_NOT_BE_USED`
- Three- and four-layer Maven archetypes, and the runnable `ddk-example-user` application
- `scripts/ddk.sh` to install DDK locally and generate projects in one command
- Null-safety: every package in `ddk-core`, `ddk-mybatis` and the starters is `@NullMarked` with JSpecify, and NullAway checks it at compile time
- Javadoc and sources jars for every module, built by the `release` profile in CI and attached to GitHub Releases; `internal` packages are left out of the Javadoc
- Quality gates in `mvn verify`: Spotless checks and per-module JaCoCo minimums (70% lines, 50% branches); CI on Java 21 and 25

### Changed

- Baseline is Spring Boot 4.1, Spring Framework 7 and Jackson 3
- `ddk-web-starter` contributes Jackson defaults through `JsonMapperBuilderCustomizer` and serializes `Long` as a string
- `ddk-tracer-starter` builds on `spring-boot-starter-opentelemetry` instead of the whole actuator
- `Entity.id()`, `AggregateRoot.version()` and `ApiResponse` data are declared `@Nullable`; `AbstractException.getArgs()` returns an empty array instead of `null`
- The Redis level of the two-level cache writes synchronously, because Spring Data Redis 4 writes asynchronously by default and that let stale values flow back into L1

### Removed

- `RedisUtil`; inject `RedisTemplate<String, Object>` directly

### Internal

Starter implementation classes live in `com.ddk.<starter>.starter.internal` packages and are not public API: `CacheInvalidationListener`, `CacheInvalidationMessage`, `RedisCacheInvalidationPublisher`, `MicrometerCacheMetrics`, `JitteredTtlFunction`, `SpringDomainEventPublisher` and `TraceIdResponseFilter`. Generated projects check this with `DDK_INTERNALS_MUST_NOT_BE_USED`.
- The springdoc dependency from `ddk-web-starter`; add it in the application when API docs are needed

[Unreleased]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/poppycoderr/domain-driven-kit/releases/tag/v0.1.0
