# Compatibility

[简体中文](./COMPATIBILITY.zh-CN.md)

What DDK promises to keep working between releases, and what it does not. Until 1.0 the promise is weaker (see [Before 1.0](#before-10)); this page is also the list of what 1.0 freezes.

## Versioning

From 1.0 on, DDK follows [semantic versioning](https://semver.org/) for everything on this page marked **stable**:

| Release | May contain |
|---|---|
| Patch (`1.2.3` → `1.2.4`) | Fixes only. No new API, no change to a documented default |
| Minor (`1.2` → `1.3`) | Additions: new types, methods, properties, rules, starters. Deprecations. Behavior fixes that make DDK do what its documentation already says |
| Major (`1.x` → `2.0`) | Anything listed under **Breaking** in the changelog, each with a migration step in the upgrade guide |

Something to be removed is first marked `@Deprecated(since = "...", forRemoval = true)`, or deprecated in the documentation for things that are not Java, in a minor release. It is removed in the next major release at the earliest.

## What is stable

### Java API

Public and protected members of the published modules, outside `*.internal` packages:

| Module | Stable types |
|---|---|
| `ddk-core` | Domain model (`AggregateRoot`, `Entity`, `Identifier`, `ValueObject`, `DomainEvent`, `AbstractDomainEvent`, `DomainEventPublisher`, `IntegrationEvent`, `Specification`), `GenericRepository` and `ConcurrentUpdateException`, paging (`PageQuery`, `PageResponse`, `Sort`), exceptions and `ErrorCode` / `CommonError`, `ApiResponse`, `OperatorContext` / `Operator` / `DataScope`, the mapper registry (`MapperProvider`, `ObjectMapper`, `EnhancedMapper`, `MapperConfiguration`, `EnumMapper`, `IEnum`), `IdentifierJacksonModule` |
| `ddk-mybatis` | `GenericRepositoryImpl`, `MybatisPlusPageAdapter`, `@Query`, `Operator`, `QueryParser` |
| `ddk-test` | `DdkAssertions`, `AggregateAssert`, `BusinessRuleAssert`, `DdkContainers` and its container types |
| Starters | The types applications inject, implement, annotate with or catch: `@AggregateLock`, `AggregateLocks`, `@Idempotent`, `@RateLimit`; `IntegrationEventConsumer`, `ReceivedEvent`, `EventContract`, `IntegrationEventDispatcher`, `IdempotentConsumer`; `Projection`, `Projections`, `FailedRefresh`; `DataScopeResolver`; `OperatorResolver`, `BaseExceptionHandler`; `CacheSettings`, `DdkCacheManager`, `TwoLevelCache`, `CacheInvalidationPublisher`, `CacheMetrics`; `RedisJsonMapper`; `CommonArchRules`, `ArchGuard`, `ArchGuardReport`, `Violation` |

Adding a method with a default implementation, or an annotation element with a default, is compatible. Changing a parameter list is not; a new parameter comes as a new overload. A new overload is binary compatible, but it can make an existing call with `null` arguments ambiguous at compile time, so each one is reviewed for that.

CI checks this on every change: japicmp compares each module with the last release, and a binary or source incompatible change fails the build unless it is listed as an intended break (see `AGENTS.md`).

### Configuration

- **Property names and defaults** under `ddk.*`, as documented in each starter's README and on the documentation site. A property may be renamed in a minor release only with the old name still working and deprecated.
- **Auto-configuration class names** (`Ddk*AutoConfiguration`), because applications name them in `spring.autoconfigure.exclude`. Their `@Bean` methods are package-private and not API; bean types and the `@ConditionalOnMissingBean` contract (your bean replaces DDK's) are.
- **What is off by default stays off.** A minor release does not turn on a feature that changes behavior.

### Data and wire formats

Data that outlives a deployment, or travels between instances on different DDK versions. During a rolling upgrade, old and new instances read each other's messages, rows and Redis entries, so both the names and the values are part of the contract.

| Contract | What is fixed | Where |
|---|---|---|
| Integration event messages | Header names `ddk-event-id`, `ddk-event-type`, `ddk-event-version`, `ddk-event-tenant`. Their values: the event ID as written by `@IntegrationEvent(id)`, the type (`type`, or the event class's simple name), the version as a decimal string, the tenant. The body is the event serialized as JSON, typed identifiers as their raw value. Topic and tag come from `@IntegrationEvent(value)` as `topic:tag`, and the message key from `key` | Event starter |
| Idempotent consumer identity | A processed message is recorded as `group:consumerId` with the event ID; `consumerId()` defaults to the consumer class's simple name. Renaming a consumer class, or changing `consumerId()`, makes it handle redelivered events again | Event starter |
| Tables `ddk_projection_pending` and `ddk_processed_message` (names configurable) | Columns, keys and the due index; a mark written by one version is processed by the next | Projection and event starters |
| The outbox (`event_publication`) | The table and its status values belong to Spring Modulith, which DDK verifies with its default JDBC structure. DDK's part is what goes in it: `event_type` is the event class's full name, and `serialized_event` is the event as JSON. Moving or renaming an event class strands its unfinished publications, so let them complete first | Event starter, Spring Modulith |
| Redis keys | Locks `ddk:lock:<type>:<id>`, idempotency `ddk:idempotent:<scope>:<key>`, rate limits `ddk:rate:<scope>:<limit>/<period>ms:<key>`, cache entries `ddk:cache:<cache>::<key>`, all with configurable prefixes. The job lock key belongs to ShedLock; DDK sets its prefix `ddk:job-lock:<application>:` | Concurrency, cache and job starters |
| Redis values | The Redis starter's JSON with type information (`@class`, and type-tagged arrays for collections and numbers). DDK keeps this encoding; the classes inside it, and how they evolve, are the application's | Redis and cache starters |
| Cache invalidation | The channel `ddk:cache:invalidation` (configurable prefix) and its message `{"origin", "cacheName", "key"}`, where a `null` key clears the cache | Cache starter |
| HTTP | `ApiResponse` JSON (`code`, `message`, `data`, `timestamp`), `CommonError` codes and their HTTP statuses, the trace ID response header (`X-Trace-Id` by default) | Core, web and tracer starters |
| The ArchGuard report format | Fields of the machine-readable report | ArchGuard starter |

A change to any of these comes with a way through a rolling deployment: old and new instances must work side by side, or the upgrade guide says that they cannot and what to do. A schema change is applied by the starter or comes with migration SQL.

These contracts are tested against data that a released version actually produced: [`compat/`](./compat/README.md) holds a generator that runs a released DDK from Maven Central, and each module reads that release's fixtures and checks that it still writes the same data. japicmp does not look at JSON, SQL, Redis values or message contents.

### Architecture rules

An existing rule in `CommonArchRules` does not become stricter in a minor release, because that would fail builds that passed before. A stricter check comes as a new rule that projects opt into, and may replace the old rule in the next major release. A rule may become more lenient when it reported something that the documentation allows.

## What is not stable

- **`*.internal` packages.** Implementation classes that only auto-configuration creates. Generated projects check this with `DDK_INTERNALS_MUST_NOT_BE_USED`.
- **Experimental modules:** `ddk-db-starter` and `ddk-seata-starter`. They are tested at the wiring level only (see the [support matrix](./ROADMAP.md#support-status)) and may change in any release. A module leaves this list when it is verified against the real system.
- **Examples** (`ddk-examples/*`): reference code, not a library.
- **Log messages, metric tag sets beyond the documented ones, and exception messages.** Write code against exception types and error codes, not message text.
- **Generated project templates.** A new release of an archetype generates a different skeleton; projects generated earlier are not changed.

## Before 1.0

In 0.x, a minor release may break anything on this page. Every break is listed under **Breaking** in the [changelog](./CHANGELOG.md), with the steps in the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade), and the API check still runs, so no break is accidental. Patch releases never break.
