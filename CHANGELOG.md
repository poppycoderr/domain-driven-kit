# Changelog

All notable changes to Domain Driven Kit are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

DDK follows [Semantic Versioning](https://semver.org/) with the usual 0.x caveat:

- **0.x**: a minor release (`0.1` → `0.2`) may contain breaking changes, and every one is listed under **Breaking** below. Patch releases (`0.1.0` → `0.1.1`) never break.
- **1.0 onwards**: breaking changes only in major releases.
- Releases are tagged `vX.Y.Z` and published as [GitHub Releases](https://github.com/poppycoderr/domain-driven-kit/releases). DDK is not on Maven Central yet; install it locally with [`scripts/ddk.sh`](./scripts/ddk.sh).

## [0.7.0] - 2026-10-09

Two mistakes that used to pass silently now fail where they are made: publishing an integration event outside a transaction, and taking an aggregate lock inside one. Both can be switched back to a warning. Scheduled jobs no longer need Redis in a single-instance environment.

### Breaking

- Acquiring an aggregate lock inside a transaction now throws `IllegalStateException`, through `AggregateLocks` and `@AggregateLock` alike. Such a lock is released before the transaction commits, so the next holder reads data from before the commit; nothing reported it. Re-entering a lock the thread already holds is not affected. Set `ddk.concurrency.lock.inside-transaction=warn` or `ignore` to keep the old behaviour. `AggregateLocks` built with the existing four-argument constructor does not check
- Publishing an integration event outside a transaction now throws `IllegalStateException`. Such an event was never delivered, and with Spring Modulith it left an incomplete publication record behind, all without a word in the log. The check runs before the event is recorded. Set `ddk.event.outside-transaction=warn` or `ignore` to keep an application running while its publishing code is fixed

### Added

- `ddk.job.lock.store=local` keeps scheduled job locks in the memory of the current process, so jobs with `@SchedulerLock` run in an environment without Redis. Single instance only, and never chosen automatically

## [0.6.0] - 2026-10-09

Both sides of integration events: declared consumers join the publishing side, and the trace follows an event from the request that caused it to the handler that processes it. `ddk-mall`, a reference application with three bounded contexts, uses every starter side by side; building it surfaced the fixes below.

### Added

- `ddk-examples/ddk-mall`: a reference application with order, inventory and payment contexts in one modular monolith. It has the order context (an aggregate with a child table, Flyway migrations, audit fields), the inventory context (reservations under per-SKU locks), their integration through the outbox and RocketMQ with idempotent consumers, and the payment context (a simulated channel, refunds when a payment crosses a cancellation) with a scheduled job that closes unpaid orders, and a read side with cached products and stock, API docs grouped by context MCP tools for a support agent, and traces and metrics exported over OTLP to a one-container observability backend, tested end to end on MySQL, Redis and RocketMQ containers
- Consuming integration events in `ddk-event-starter`: declare an `IntegrationEventConsumer` bean and DDK subscribes to RocketMQ (orderly by default), decodes the payload, exposes the contract headers as `ReceivedEvent` and can deduplicate through the inbox. `ddk.event.local-delivery.enabled` delivers in-process for development, and `IntegrationEventDispatcher` lets listeners of other brokers reuse the same handling
- Integration events carry the trace: `ddk-event-starter` observes publishing and consuming (`ddk.event.publish`, `ddk.event.consume`), writes the trace context into the message headers and continues from it on the consuming side, over RocketMQ and local delivery alike. `ddk-tracer-starter` registers a `ContextPropagatingTaskDecorator` (`ddk.tracer.async-propagation.enabled`) so the trace reaches `@Async` methods and the post-commit event delivery
- `AggregateLocks.executeAll(type, ids, action)` locks several aggregates of one type in a fixed order, so two operations that need the same aggregates cannot deadlock on each other
- `GenericRepositoryImpl` has extension points for aggregates stored in a root table plus child tables: `afterInsert`, `afterUpdate`, `afterLoad` and `beforeRemove` run around the root table's reads and writes in the same transaction

### Fixed

- Numbers in error messages were written with grouping separators: an order ID `1234567` in `订单不存在：{0}` came out as `1,234,567`, which nobody can search for. `ErrorCode.getMessage(args)` now writes numbers as they are; a placeholder with an explicit format such as `{0,number,#,##0.00}` is still honoured
- Trace export failed at runtime (`NoSuchMethodError: okio.Okio.socket`) in applications that use RocketMQ and the tracer starter together: `rocketmq-client` brings an old okio that Maven preferred over the one the OpenTelemetry exporter's OkHttp needs. The BOM now manages okio to the version OkHttp 5 requires
- The Redis starter failed the application at startup when no `RedisConnectionFactory` existed, for example with Spring Boot's Redis auto-configuration excluded for a local profile. This also defeated the cache starter's local-only fallback. The `redisTemplate` bean is now created lazily, so the application starts and only using the template without a connection fails
- The event starter's documentation claimed that events with the same key keep their order. A key only puts them in the same partition or queue; delivery after commit is concurrent by default and can send them out of order. The README now explains this and how to get ordered delivery

## [0.5.0] - 2026-10-03

One operator context for cross-cutting persistence concerns: audit fields and fail-closed multi-tenancy in the MyBatis starter, set per request by the web starter. ArchGuard fix hints are now available in English.

### Added

- `Operator` and `OperatorContext` in `ddk-core` (`com.ddk.core.context`): who is acting and for which tenant, with `runAs` / `callAs` that restore the previous value and `wrap` to carry the operator to another thread
- The web starter sets `OperatorContext` for each request when the application declares an `OperatorResolver` bean
- The MyBatis starter fills `createBy` and `updateBy` from the operator (`String` or `Long` fields), next to `createTime` and `updateTime`
- Multi-tenancy in the MyBatis starter (`ddk.mybatis.tenant.enabled=true`): every statement gets the tenant condition from `OperatorContext`, and a statement without a tenant fails instead of reading all tenants; `ignore-tables` and MyBatis-Plus's `@InterceptorIgnore` are the exemptions
- ArchGuard fix hints are available in English and Chinese. The language follows the JVM's default locale and can be set with the system property `ddk.archguard.language` (`en` or `zh`)

### Changed

- The reasons attached to the rules in `CommonArchRules` (the `because` clauses shown in reports) are now in English

## [0.4.1] - 2026-10-03

### Fixed

- The ArchGuard report named the wrong class for a violation inside a constructor or static initializer: it reported the class being depended on instead of the class that contains the violation

## [0.4.0] - 2026-10-02

Middleware integrations with domain semantics: aggregate locks, duplicate-submit protection and rate limits on Redisson, scheduled jobs that run on one instance, the error contract in OpenAPI docs, Flyway migrations in generated projects, and `ddk-test` for domain assertions and container presets.

### Added

- `ddk-concurrency-starter`: `@AggregateLock` holds a Redisson lock per aggregate instance around the method's transaction, `@Idempotent` rejects a request key that was already submitted, and `AggregateLocks` is the programmatic entry point
- `@RateLimit` in `ddk-concurrency-starter` gives each key, such as a user or tenant, a budget of calls per period shared by all instances; over the limit it throws `RateLimitedException` (`RATE_LIMITED`), which the web starter maps to 429
- `AggregateBusyException` (`AGGREGATE_BUSY`) and `DuplicateRequestException` (`DUPLICATE_REQUEST`) in `ddk-core`; the web starter maps both to 409 Conflict
- The web starter documents the error contract when springdoc is on the classpath: every operation gets 400 / 409 / 500 responses with the failed `ApiResponse` body, and `components.schemas.ErrorCode` lists `CommonError` plus every `ErrorCode` enum in the application's packages with its message. `ddk.web.openapi=false` turns it off. The BOM manages springdoc 3.1; the user example serves Swagger UI
- `ddk-test`: `DdkAssertions` for aggregates (`hasRaisedExactly`, `hasRaised`, `hasRaisedNoEvents`) and rejected operations (`assertThatRejected(...).withCode(...)`), and `DdkContainers` presets for Redis, MySQL and RocketMQ. Generated projects and the example depend on it with test scope
- `ddk-job-starter`: turns on `@Scheduled` and wires ShedLock with a Redis lock, so a job marked `@SchedulerLock` runs on one instance at a time; defaults under `ddk.job.lock.*`. The optional rule `CommonArchRules.SCHEDULED_JOBS_MUST_BE_LOCKED` fails the build when a `@Scheduled` method has no `@SchedulerLock`
- `CommonArchRules.SCHEDULED_JOBS_MUST_RESIDE_IN_ADAPTER` keeps `@Scheduled` and `@XxlJob` methods in the adapter layer, so scheduled jobs reach the domain only through application services; generated projects and the example check it

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

[0.7.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/poppycoderr/domain-driven-kit/releases/tag/v0.1.0
