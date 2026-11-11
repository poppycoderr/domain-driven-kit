# Compatibility

[简体中文](./COMPATIBILITY.zh-CN.md)

What DDK promises to keep working between releases, and what it does not. Until 1.0 the promise is weaker (see [Before 1.0](#before-10)); this page is also the list of what 1.0 freezes, and the [freeze checklist](#10-freeze-checklist-draft) says what is still open.

## Versioning

From 1.0 on, DDK follows [semantic versioning](https://semver.org/) for everything on this page marked **stable**:

| Release | May contain |
|---|---|
| Patch (`1.2.3` → `1.2.4`) | Fixes only. No new API, no change to a documented default |
| Minor (`1.2` → `1.3`) | Additions: new types, methods, properties, rules, starters. Deprecations. Behavior fixes that make DDK do what its documentation already says |
| Major (`1.x` → `2.0`) | Anything listed under **Breaking** in the changelog, each with a migration step in the upgrade guide |

Something to be removed is first marked `@Deprecated(since = "...", forRemoval = true)`, or deprecated in the documentation for things that are not Java, in a minor release. It is removed in the next major release at the earliest.

## Platform

What DDK runs on is part of the contract too, but not every version that happens to work is:

| | In 1.x |
|---|---|
| Java | The minimum is Java 21 for all of 1.x; raising it is a major release. The JDKs that CI tests are listed in [SUPPORT.md](./SUPPORT.md#what-ddk-is-tested-against) |
| Spring Boot | 1.x is built for Spring Boot 4. A minor release may move to a newer Spring Boot 4.x minor, listed in the changelog with its upgrade notes; a patch release keeps the Spring Boot minor. Spring Boot 5 means DDK 2.0 |
| Libraries whose versions DDK pins (Spring Modulith, MyBatis-Plus, Redisson, ShedLock, the RocketMQ client, springdoc, Spring AI, Okio) | A patch release takes their patch updates, a minor release may take minor updates. An update that makes applications change their code is a major-release change, with one exception: a security fix ships when it is needed, listed under **Breaking** with a migration note |
| Library types in DDK's API | `GenericRepositoryImpl` extends MyBatis-Plus's `ServiceImpl`, and `RedisJsonMapper.create` returns Jackson's `ObjectMapper`. DDK keeps these in 1.x, but the inherited methods are the library's and change with it, under the rule above. The API check compares DDK's own classes only, so it does not catch such a change |
| Overriding a managed version | Allowed, but only the versions in [SUPPORT.md](./SUPPORT.md#what-ddk-is-tested-against) are tested; a different one is for the application to verify |
| Middleware and databases | The versions in SUPPORT.md are tested. Others may work, without a promise |

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
- **Experimental modules:** none at present. A module that is tested at the wiring level only is listed here, may change in any release, and leaves the list when it is verified against the real system, as `ddk-db-starter` did in 0.12. `ddk-seata-starter`, the other one, was deprecated in 0.12 and removed in 1.0.
- **Examples** (`ddk-examples/*`): reference code, not a library.
- **Log messages, metric tag sets beyond the documented ones, and exception messages.** Write code against exception types and error codes, not message text.
- **Generated project templates.** A new release of an archetype generates a different skeleton; projects generated earlier are not changed.

## 1.0 freeze checklist (draft)

A draft for the 1.0 decision, written after the 0.10 → 0.11 rehearsal. It says, for each part of the contract above, what 1.0 would freeze, what checks it, and what was decided. Nothing here is a promise until 1.0 is released; until then [Before 1.0](#before-10) applies.

### Frozen in 1.0

| Contract | Checked by | Not covered |
|---|---|---|
| [Java API](#java-api) of the stable types | japicmp against the last release on every change | Inherited library methods ([Platform](#platform)) |
| [Configuration](#configuration): `ddk.*` names and defaults, `Ddk*AutoConfiguration` names, your bean replaces DDK's | Wiring tests of each starter | No automated check that a property keeps its name; reviewed by hand |
| [Data and wire formats](#data-and-wire-formats): event headers and body, consumer identity, the projection and processed message tables, outbox content, Redis keys and values, cache invalidation | Fixtures of every release since 0.10.0 in [`compat/`](./compat/README.md), read and written back by every build; the 0.10 → 0.11 rehearsal with both versions side by side, and the 0.11 → 0.12 upgrade and rollback rehearsal | Releases before 0.10.0 have no fixtures; the outbox table itself is Spring Modulith's |
| HTTP: `ApiResponse`, `CommonError` codes and statuses, the trace ID header | Web and tracer starter tests | |
| [Architecture rules](#architecture-rules): an existing rule never becomes stricter | Fixtures for every rule | |
| [Platform](#platform): Java 21 minimum, Spring Boot 4 line, pinned libraries | CI on Java 21 and 25 | |

The behavior that 1.0 freezes is the documented one, not more:

- Event delivery is at least once. `IdempotentConsumer` skips a message whose consumer identity (`group:consumerId`) and event ID are already recorded. That holds while both stay stable, while the record is within the inbox retention, and because the record and the handler's database writes commit in one transaction: a handler that fails rolls back its record, and a purged record lets the message run again. Side effects outside that database are not exactly-once. It also needs event IDs that are unique across tenants and messages from a source the consumer trusts; the tenant header is not signed.
- `@Idempotent` rejects a call while a registration for its key exists. A call that fails removes its own registration, so it can be retried; the registration of a call that succeeded expires with its TTL, after which the same key runs again; a call that runs longer than the TTL can overlap with a retry. It does not make side effects exactly-once.
- An aggregate lock is as reliable as a single Redis. An interrupt while waiting is a cancellation (`CancellationException`), not a busy lock.
- A projection converges to the current state of the write model; it can briefly show an older snapshot.

### Experimental in 1.0

| Part | Why | Leaves the list when |
|---|---|---|
| GraalVM native images beyond the modules [SUPPORT.md](./SUPPORT.md#graalvm-native-images) lists as verified | CI runs `ddk-example-user` and `ddk-example-inventory` as native images, which cover the modules SUPPORT.md lists | CI exercises the module natively |
| How the documented metrics are computed | The metric names and documented tags are stable. Event publications are counted in the background at an interval, and each count still scans `event_publication`, a known cost on large tables | |
| Everything under [What is not stable](#what-is-not-stable) | | |

### Decided for 1.0

The questions that this draft left open, as the maintainer decided them on 2026-11-10. The changes they need land in 0.12, so that 1.0 itself only freezes.

| Question | Decision | Done in |
|---|---|---|
| When to release 1.0 | After 0.12, not right after 0.11. 0.12 makes the changes below while a minor release may still break; 1.0 adds no code, only the freeze, the rehearsal and the documentation | the roadmap |
| The HTTP answer to a cancelled request | A code of its own: `CommonError.REQUEST_CANCELLED` with 503, instead of 500 `SYSTEM_ERROR`. The request was fine and can be retried, and it no longer counts as a server fault | 0.12, listed under **Breaking** |
| Whether the processed message table's key includes the tenant | No: the key stays `(consumer, message_id)`. Event IDs unique across tenants and a trusted source stay premises of `IdempotentConsumer`, now stated in its Javadoc and README where it is used | 0.12, documentation |
| How wide the ArchGuard exemption for `@Configuration` classes outside the layers stays | As wide as it is. A new, optional rule, `WIRING_CLASSES_ONLY_WIRE`, keeps those classes to `@Bean` methods and supertype overrides and stops them from calling mappers; generated projects check it | 0.12 |
| DB and Seata starters | `ddk-db-starter` is tested on PostgreSQL and MySQL and is no longer experimental; 1.0 freezes it with the other starters. `ddk-seata-starter` is deprecated in 0.12, and 1.0 neither publishes it nor lists it in the BOM | 0.12 deprecates Seata; 1.0 removes it |

### Before tagging 1.0

- [x] The decisions above are made and written into this page
- [x] 0.11.0 is released and its fixtures are added under `compat/`; `api.baseline.version` is 0.11.0
- [x] 0.12.0 carries the changes the decisions need, is released, and its fixtures and API baseline follow the same way; `ddk-seata-starter` is removed
- [ ] An upgrade and rollback rehearsal from 0.12 to the 1.0 candidate, on a project generated from 0.12.0, the same way as from 0.11 to 0.12
- [ ] The candidate passes Java 21 and 25, generated projects, the API check and the native image job
- [ ] This section's "draft" is removed, and the [support matrix](./ROADMAP.md#support-status) matches this page

## Before 1.0

In 0.x, a minor release may break anything on this page. Every break is listed under **Breaking** in the [changelog](./CHANGELOG.md), with the steps in the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade), and the API check still runs, so no break is accidental. Patch releases never break.
