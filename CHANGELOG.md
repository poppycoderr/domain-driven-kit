# Changelog

All notable changes to Domain Driven Kit are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

DDK follows [Semantic Versioning](https://semver.org/) with the usual 0.x caveat:

- **0.x**: a minor release (`0.1` → `0.2`) may contain breaking changes, and every one is listed under **Breaking** below. Patch releases (`0.1.0` → `0.1.1`) never break.
- **1.0 onwards**: breaking changes only in major releases.
- Releases are tagged `vX.Y.Z` and published as [GitHub Releases](https://github.com/poppycoderr/domain-driven-kit/releases). DDK is not on Maven Central yet; install it locally with [`scripts/ddk.sh`](./scripts/ddk.sh).

## [Unreleased]

### Added

- `ddk-concurrency-starter`: `@AggregateLock` holds a Redisson lock per aggregate instance around the method's transaction, `@Idempotent` rejects a request key that was already submitted, and `AggregateLocks` is the programmatic entry point
- `AggregateBusyException` (`AGGREGATE_BUSY`) and `DuplicateRequestException` (`DUPLICATE_REQUEST`) in `ddk-core`; the web starter maps both to 409 Conflict

### Changed

- Generated projects and the user example manage their schema with Flyway (`spring-boot-starter-flyway`, scripts in `src/main/resources/db/migration`) instead of `schema.sql`; the generated `AGENTS.md` and skills tell agents to add a new migration per schema change

## [0.3.0] - 2026-10-01

Reliable domain events: a transactional outbox on Spring Modulith's event publication registry, delivery to Kafka, RocketMQ, AMQP and JMS, event contracts, and idempotent consumers. The repository contract now reports optimistic-lock conflicts.

### Added

- `@IntegrationEvent` in `com.ddk.core.domain` marks domain events for delivery outside the process and names their target and message key, without framework annotations in the domain layer
- `ddk-event-starter` hands `@IntegrationEvent` events to Spring Modulith's event externalization when it is on the classpath, so they are recorded in the business transaction and delivered after commit; the BOM manages Spring Modulith 2.1
- `@IntegrationEvent(id = ...)` puts the event's own identifier into the `ddk-event-id` message header, so it stays stable across resubmissions
- `@IntegrationEvent(type, version)` and the `ddk-event-type` / `ddk-event-version` headers give each integration event a stable contract name and version
- The event starter verifies `@IntegrationEvent` declarations at startup (accessors, version, duplicate contracts) instead of failing at delivery time
- The event starter delivers `@IntegrationEvent` events to RocketMQ when `rocketmq-client` is on the classpath and a `DefaultMQProducer` exists or `ddk.event.rocketmq.name-server` is set: `topic:tag` targets, per-key queue ordering, contract headers as user properties; the BOM manages the client version
- `IdempotentConsumer` (`ddk.event.inbox.enabled=true`) runs a handler once per consumer and message ID, registering the message in the handler's transaction behind a savepoint
- `ConcurrentUpdateException` (`CONCURRENT_UPDATE`), which the web starter maps to 409 Conflict
- `IdentifierJacksonModule` writes typed identifiers such as `UserId` as their raw value and reads them back through the subclass's `of(...)` factory; the web and event starters register it. Without it Jackson wrote identifiers as `{}`, so a serialized domain event lost its IDs
- `GenericRepositoryImpl` accepts typed identifiers such as `UserId` and unwraps them to key values; the example and archetype skills use `GenericRepository<User, UserId>`

### Fixed

- `GenericRepositoryImpl.update` silently ignored an optimistic-lock conflict or a removed row and still published the aggregate's domain events; it now throws `ConcurrentUpdateException` and publishes nothing
- `updateAll` checks each row's version instead of a batch update that cannot report per-row conflicts


## [0.2.0] - 2026-09-18

AI collaboration: guardrails for coding agents in generated projects, actionable architecture reports, and use cases as MCP tools.

### Added

- Generated projects ship `AGENTS.md` and `CLAUDE.md` describing layers, conventions and the `mvn verify` gate for AI coding agents
- Claude Code Skills in generated projects: `ddk-add-aggregate`, `ddk-add-use-case` and `ddk-add-domain-event` for the four-layer template, `ddk-add-feature` for the three-layer template
- Generated projects include `spring-boot-starter-webmvc-test` for MockMvc tests
- `AGENTS.md` and `CLAUDE.md` for contributors to DDK itself
- `ddk-mcp-starter`: exposes application use cases as MCP tools on top of Spring AI 2.0, with streamable HTTP by default, Bean Validation on tool arguments, error-coded tool errors, hidden internals for unexpected exceptions and an audit log line per call
- `CommonArchRules.MCP_TOOLS_MUST_RESIDE_IN_ADAPTER` keeps `@McpTool` methods in the adapter layer
- The user example exposes `register_user`, `get_user` and `disable_user` as MCP tools
- `ArchGuard.check` evaluates all architecture rules at once and writes `target/archguard/violations.json` and `violations.md`, each violation with its rule, class and a concrete fix; generated projects and the example use it

### Changed

- Generated projects and the example check architecture in one `ArchitectureTest.architectureIsRespected()` through `ArchGuard.check`, instead of one test per rule
- The planned `ddk-ai-starter` is dropped: chat client configuration, structured output and token usage metrics are built into Spring AI 2.0

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

[Unreleased]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/poppycoderr/domain-driven-kit/releases/tag/v0.1.0
