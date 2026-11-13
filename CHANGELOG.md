# Changelog

All notable changes to Domain Driven Kit are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

DDK follows [Semantic Versioning](https://semver.org/) with the usual 0.x caveat:

- **0.x**: a minor release (`0.1` → `0.2`) may contain breaking changes, and every one is listed under **Breaking** below. Patch releases (`0.1.0` → `0.1.1`) never break. The [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) lists what to do for each minor release, including schema migrations and behavior changes that are not API breaks.
- **1.0 onwards**: breaking changes only in major releases.
- Releases are tagged `vX.Y.Z` and published as [GitHub Releases](https://github.com/poppycoderr/domain-driven-kit/releases). From 0.10.0 on they are also on Maven Central under `com.domaindrivenkit`; earlier releases (groupId `com.ddk`) and snapshots are installed locally with [`scripts/ddk.sh`](./scripts/ddk.sh). [COMPATIBILITY.md](./COMPATIBILITY.md) says what each kind of release may change.

## [Unreleased]

## [0.12.2] - 2026-11-13

A security release for the 0.12 line, with the same fix as 1.0.1. It fixes a way to inject SQL through the row-level data scope of `ddk-mybatis-starter` when group or owner ids are strings. Applications that set `ddk.mybatis.data-scope.numeric-group-id` or `numeric-owner-id` to `false` should upgrade. Nothing else changes, and nothing needs to change in the application. The advisory is [GHSA-9cw9-2935-ch77](https://github.com/poppycoderr/domain-driven-kit/security/advisories/GHSA-9cw9-2935-ch77).

### Security

- A string data scope id can no longer inject SQL. With string group or owner ids, the data scope condition doubled single quotes but kept backslashes, which MySQL treats as escapes by default: a group id ending in a backslash made the next group id SQL, and read the rows of every tenant. A string data scope id that contains a backslash or a control character is now rejected before any SQL runs; ids with single quotes still work. Numeric ids, the default, were not affected. Affects every release since 0.8.0

## [0.12.1] - 2026-11-11

A security release. It fixes two ways to inject SQL through `ddk-mybatis` and `ddk-mybatis-starter`: the sort field of a page query, and a string tenant id. Every application that accepts a `PageQuery` from a request should upgrade; applications with string tenant ids should upgrade too. Nothing else changes, and nothing needs to change in the application. The advisories are [GHSA-67m2-4mxv-mj3p](https://github.com/poppycoderr/domain-driven-kit/security/advisories/GHSA-67m2-4mxv-mj3p) and [GHSA-3wwm-gcpx-5ch7](https://github.com/poppycoderr/domain-driven-kit/security/advisories/GHSA-3wwm-gcpx-5ch7).

### Security

- A sort field of a page query can no longer inject SQL. `QueryParser` passed `Sort.sortField` to MyBatis-Plus's `orderBy`, which puts the column into `ORDER BY` as it is, so a controller that accepts a `PageQuery` in its request body let the caller run a subquery in the database. A sort field must now be an identifier (`[A-Za-z_][A-Za-z0-9_]*`); anything else fails with `VALIDATION_ERROR`, which the web starter answers with 400. Affects every release since the first one
- A string tenant id can no longer inject SQL. With `ddk.mybatis.tenant.numeric-id=false`, the tenant was written into the tenant condition without escaping, so a tenant id with a quote read the rows of every tenant. Tenant ids can come from a gateway header or the unsigned `ddk-event-tenant` message header. A string tenant id that contains a quote, a backslash or a control character is now rejected before any SQL runs. Numeric tenant ids, the default, were not affected

## [0.12.0] - 2026-11-10

The last minor release before 1.0. It carries out the decisions that the 1.0 freeze checklist left open, while a minor release may still break, so that 1.0 itself only freezes. A cancelled request is answered with 503 and `REQUEST_CANCELLED` instead of 500, the one breaking change. `ddk-db-starter` is tested on PostgreSQL and MySQL and is no longer experimental, and `ddk-seata-starter` is deprecated ahead of its removal in 1.0. Publication metrics are sampled in the background, so their cost no longer follows the scrape rate. Native images are verified for the Redis-backed starters, projections and the publication registry, projects generated on JDK 23 and later compile Lombok, an optional ArchGuard rule keeps configuration classes outside the layers to wiring, and every starter's README starts with a 30-second setup and ends with its common errors. The [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) lists the steps.

### Breaking

- A request whose thread is interrupted, which `AggregateLocks` and `@AggregateLock` report as `CancellationException` since 0.11.0, is answered by `BaseExceptionHandler` with 503 and the new error code `REQUEST_CANCELLED` instead of 500 and `SYSTEM_ERROR`. It happens, for example, when the application shuts down while a request waits for a lock: the request itself was fine and can be retried, usually on another instance, and it no longer counts as a server error in 5xx-by-code alerts on `SYSTEM_ERROR`. An MCP tool reports `REQUEST_CANCELLED` too. To keep the old response, declare an `@ExceptionHandler(CancellationException.class)` in your own `@RestControllerAdvice` that returns 500 with `ApiResponse.ofFail(CommonError.SYSTEM_ERROR)`

### Added

- Native images verified for the concurrency, Redis, cache and projection starters, and for listeners that run through Spring Modulith's JDBC publication registry: CI builds a second example, `ddk-example-inventory`, against Redis and smoke-tests it. [SUPPORT.md](./SUPPORT.md#graalvm-native-images) lists what is checked
- `ddk-concurrency-starter` registers, ahead of time, the reflection that the key expressions of `@AggregateLock`, `@Idempotent` and `@RateLimit` need on a native image: the parameter types of the annotated methods, as for JSON binding. Applications no longer register them
- `ddk.event.publications.sample.age`, the seconds since the publication counts were last sampled, and the properties `ddk.event.metrics.publications.enabled` and `ddk.event.metrics.publications.interval`
- `CommonError.REQUEST_CANCELLED`, answered with 503, and a `503` response in the error contract that the web starter adds to the API docs
- `CommonArchRules.WIRING_CLASSES_ONLY_WIRE`, an optional rule: a `@Configuration` class outside the layers, which the layering rules exempt, may only declare `@Bean` methods or override supertype methods, and may not call a mapper of the infrastructure layer. Projects generated from the archetypes check it; existing projects can add it to their architecture test. The existing rules are unchanged

### Changed

- `ddk.event.publications` is sampled in the background instead of being counted on every scrape. Spring Modulith's `event_publication` table has no index on `status`, so each count scans the table; every scrape used to run four of them on every instance. A sample now runs every `ddk.event.metrics.publications.interval` (1 minute by default), so the database load no longer follows the scrape rate, and a value can be up to one interval old. A failed sample keeps the previous values; after three failed samples in a row the gauges report NaN. Metric names and tags are unchanged
- `ddk-db-starter` is no longer experimental. Its tests now run against PostgreSQL 17 and MySQL 8.4 as two data sources of one application: each source reaches its own database, the configured one is primary, and each transaction manager commits and rolls back only its own source. The 1.0 freeze covers it like the other starters

### Deprecated

- `ddk-seata-starter`. 1.0 removes it from `ddk-dependencies` and stops publishing it: it has wiring tests only, Seata's runtime is not verified on Spring Boot 4, and global transactions go against the outbox and eventual consistency that DDK builds on. Its auto-configuration logs a warning at startup. To keep using it, pin 0.12.x or copy its three classes into your project

### Fixed

- A native image that uses Spring Modulith's JDBC publication registry starts. The event starter wraps the event multicaster to check publications outside a transaction, and created that proxy class at runtime, which a native image cannot do; the class is now generated ahead of time
- The cache starter's local level works on a native image: it registers the Caffeine classes that its configuration builds, and the invalidation message it sends
- Projects generated from the archetypes compile Lombok annotations on JDK 23 and later. Since JDK 23, javac no longer runs annotation processors it finds on the classpath, so `@RequiredArgsConstructor` and the like did nothing and compilation failed with `variable ... not initialized in the default constructor`. The generated `pom.xml` now lists Lombok under the compiler's `annotationProcessorPaths`, and the archetype tests check it. In a project generated earlier, add the same `maven-compiler-plugin` configuration (see the archetype `pom.xml`); list any other annotation processor you use there too, because only the listed ones run

## [0.11.0] - 2026-11-10

Settling the contracts that 1.0 will freeze. COMPATIBILITY.md now names the data and wire formats a rolling upgrade depends on and what each kind of release may change on the platform, and drafts the 1.0 freeze checklist. Data that 0.10.0 wrote is read and written back the same way by tests, including the idempotency and rate-limit keys. Two behavior changes need attention when upgrading: the default scope of `@Idempotent` and `@RateLimit`, which changes their Redis keys, and cancellation while waiting for a lock. Projections converge in one more failure case and refresh right after the commit on MySQL, an application's cache invalidation publisher replaces DDK's, and `ddk-example-user` runs as a GraalVM native image in CI. The upgrade and the rollback were rehearsed with 0.10 and 0.11 instances side by side; the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) lists the steps.

### Breaking

- The default scope of `@Idempotent` and `@RateLimit` names parameter types in full: `com.acme.OrderService#place(com.acme.PlaceOrderCommand)` instead of `...#place(PlaceOrderCommand)`. Overloads whose parameter types share a simple name, such as `submit(java.util.Date)` and `submit(java.sql.Date)`, used to share registrations and rate limits. The Redis keys change. For as long as old and new instances run side by side, they keep separate registrations and separate rate-limit quotas: a request registered by one version is not recognized as a duplicate by the other, and each version counts its own calls up to the limit. After the last old instance has stopped, a retry of a request that it registered still runs on the new version until that registration expires, at most one TTL later. Rolling back reads the old keys again, so registrations and counts that the old version wrote and that have not expired yet apply again. To keep the old keys, set `scope` on the annotation to the old value; the key stays the same only while the key prefix, the value of the `key` expression and, for `@RateLimit`, `limit` and `period` stay the same too
- `AggregateLocks` and `@AggregateLock` throw `CancellationException` when the thread is interrupted while it waits for a lock, and keep the interrupt flag; the action does not run. They used to throw `AggregateBusyException`, which the web starter answers with 409, so an operation cancelled during shutdown or by cancelling its task was reported as a conflict to retry. To migrate: code that caught `AggregateBusyException` to retry does not catch the cancellation, and should not retry it as if the lock were busy; a task that is cancelled ends with `CancellationException` and its thread stays interrupted. Through `BaseExceptionHandler` the request now gets 500 with `SYSTEM_ERROR`. An application that prefers 503 adds its own `@ExceptionHandler(CancellationException.class)` returning `ApiResponse.ofFail(CommonError.SYSTEM_ERROR)` with that status

### Added

- GraalVM native images, verified module by module: `ddk-mybatis-starter` registers the reflection that MyBatis-Plus and the default snowflake ID generator need, and CI builds `ddk-example-user` as a native image and smoke-tests it. `ddk-core`, `ddk-mybatis`, the web, MyBatis and MCP starters, and in-application domain events are verified; [SUPPORT.md](./SUPPORT.md#graalvm-native-images) lists the rest

### Changed

- The three projection gauges (`ddk.projection.pending`, `failing`, `oldest.age`) share one scan of the pending table per scrape instead of three, reused for a second. With a backlog of one million marks the scrape cost on MySQL 8.4 dropped from about 1.75 s to about 0.5 s

### Fixed

- Projections converge when a refresh writes after losing its claim and then fails, for example when an external write went through but its response was lost. The failure used to release the claim without marking the entry again, so the read model could keep the stale snapshot with no mark left. Now any acknowledgement or failure from a worker that no longer owns the mark leaves a mark behind; a mark that still exists only gets a new generation, which keeps the attempts and back-off of the worker that holds it
- On MySQL, a projection refresh starts right after the commit, instead of sometimes waiting for the next sweep (30 s by default). The pending table stores `available_at` without fractional seconds, and MySQL rounds them on insert, so a mark written in the second half of a second was due only in the next second and the wake-up after the commit did not see it. Marks are now written due at the start of their second. H2 and PostgreSQL were not affected
- An application's own `CacheInvalidationPublisher` replaces the Redis one without failing startup. The Redis listener container used to require DDK's internal publisher, so a replacement made the context fail when Redis was present and a cache used its local level. DDK now subscribes to its channel only when it also publishes on it

## [0.10.0] - 2026-10-22

Getting ready to freeze 1.0. DDK now publishes under its own Maven namespace, `com.domaindrivenkit`, which Maven Central can verify. Java packages stay `com.ddk.*`, so upgrading changes build files, not code. Auto-configurations follow one naming rule, and their `@Bean` methods are no longer public API. `ddk-core` stops bringing Hutool along. Release artifacts carry build provenance, and a `central` profile signs and bundles a release for Maven Central, where 0.10.0 itself was published. CI runs in named stages and fails when a test is skipped. The [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) lists the steps from 0.9.

### Breaking

- The Maven groupId changes from `com.ddk` to `com.domaindrivenkit`, a namespace that DDK owns through its domain and that Maven Central can verify. Artifact IDs and Java packages (`com.ddk.*`) stay the same, so only build files change: the BOM import, every DDK dependency and `-DarchetypeGroupId`. `scripts/ddk.sh` reads the groupId of the version it installs, so older releases keep working with it
- Three auto-configurations take the `Ddk` prefix like the other fourteen: `MybatisPlusAutoConfiguration` is now `DdkMybatisAutoConfiguration` (it shared its simple name with MyBatis-Plus's own), `WebAutoConfiguration` is now `DdkWebAutoConfiguration` and `WebOpenApiAutoConfiguration` is now `DdkWebOpenApiAutoConfiguration`. Update `spring.autoconfigure.exclude` entries and tests that name them
- `@Bean` methods of the cache, event, MyBatis, Redis and web auto-configurations are package-private, as in the other starters. The container calls them; an application that called one directly creates the object itself or injects the bean

### Added

- Release artifacts carry SLSA build provenance attestations. `gh attestation verify <jar> --repo poppycoderr/domain-driven-kit` checks that a jar or the BOM was built from this repository by the release workflow for that tag

### Changed

- An event skipped because its version is not in the consumer's contract is logged at WARN only the first time per consumer and version, then at DEBUG. While v1 and v2 are published side by side, a consumer that only knows v1 no longer logs a warning for every v2 message
- `ddk-core` no longer brings Hutool in transitively; it never used it. `ddk-mybatis` and `ddk-mybatis-starter`, which do, declare `hutool-core` themselves. An application that uses Hutool directly and got it only through `ddk-core` declares it itself

## [0.9.0] - 2026-10-21

Reliability and the production path. Projections converge when refreshes overlap, `@Idempotent` releases only its own registration, idempotent event consumers have a stable identity, and the four-layer rule keeps application services off infrastructure. Background work carries its tenant: projection marks, integration events, `@Async` tasks. Projections and event publications get operations: failure lists, retries, metrics and scheduled maintenance. `ddk-mall` recovers lost payments and has a `secure` profile. Upgrading from 0.8 takes a few steps, all listed in the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade).

### Breaking

- `LAYERED_ARCHITECTURE_RULE` no longer lets application services reference infrastructure classes. The four-layer documentation always said that infrastructure only implements domain ports; the rule now agrees, as the three-layer rule already did. Depend on the domain port instead. Wiring that has to name an implementation goes into a `@Configuration` class outside the layers, which both layering rules now exempt
- `Projections` is now an interface; the implementation moved to the starter's `internal` package. Its public constructor took `internal` types, so no application could call it without breaking the internal-package rule. Injecting `Projections` is unchanged, a test can stub it without a mocking library, and an application bean of the type replaces the starter's

### Added

- `DdkContainers.elasticsearch()` in `ddk-test`: a single-node Elasticsearch 9 without authentication, for integration tests
- `ddk-mall` can keep its order search read model in Elasticsearch (`search` profile). The projection, the API and the MCP tool are unchanged; only the implementation of the `OrderSearchIndex` port is swapped
- `IntegrationEventConsumer.contract()` and `EventContract`: a consumer declares the event type and versions it understands. Messages of another type are not given to it, and messages of another version are skipped with a warning, or rejected for redelivery, before their payload is decoded. Without a declaration nothing changes. `ddk-mall`'s consumers declare their contracts
- Projections in multi-tenant applications: a mark records the tenant of the current `OperatorContext`, and the worker refreshes it as the system operator `ddk-projection` in that tenant, also after a restart or on another instance. An ID that is only unique within a tenant no longer shares one mark across tenants. A mark without a tenant still fails against `ddk-mybatis-starter`'s tenant interceptor and is retried, instead of reading every tenant's data
- Tenants for integration events: `@IntegrationEvent(tenant = "...")` names the accessor whose value is sent in the `ddk-event-tenant` header, and a consumer whose `runInEventTenant()` returns `true` handles the event, including its inbox registration, as the system operator `ddk-event` in that tenant. A message without the header fails instead of running without a tenant. Consumers that do not opt in are unchanged
- `ddk-job-starter` and `ddk-mcp-starter` document how their entry points get an operator and tenant: a scheduled job runs each tenant through `OperatorContext.runAs` as a system operator; an MCP tool on the default synchronous, streamable server sees the operator that the HTTP filter set, which a new test pins
- Projection operations: `Projections.failures(name, limit)` lists marks that keep failing with their last error, and `Projections.retryFailed(name)` retries them at once. With Micrometer present the starter publishes `ddk.projection.refresh` (timer by outcome) and the `ddk.projection.pending`, `ddk.projection.failing` and `ddk.projection.oldest.age` gauges
- Event maintenance (`ddk.event.maintenance.enabled`, off by default): on a schedule, resubmits failed publications through Spring Modulith, deletes old completed publications and purges old inbox records, under a ShedLock lock when the application has a `LockProvider`. With Micrometer present, `ddk.event.publications{status}` counts publications by status
- `ddk-mall` recovers payments whose outcome was lost: the attempt is committed before the channel is called, settlement runs in its own transaction and refunds when the order was cancelled meanwhile, and `ReconcilePaymentsJob` queries the channel for payments that were charged but never settled. `PaymentChannel` gains `query`; the simulated channel keeps an in-memory ledger
- `ddk-mall` has a `secure` profile: requests need a login, the customer comes from the authenticated user instead of the `X-Customer-Id` header, rebuilds, stock changes and actuator are for the support role, and an MCP tool's `customerId` argument must be the signed-in customer unless support is calling. The default profile stays an open development mode
- `CommonArchRules.domainMustNotDependOn(String...)`: the domain purity rule with packages of your own added, such as an internal SDK

### Changed

- The default scope of `@Idempotent` and `@RateLimit` is now the full class name, method name and parameter types (`com.acme.OrderService#place(PlaceOrderCommand)`) instead of `OrderService.place`, so classes with the same simple name and overloaded methods no longer share registrations or quotas. The Redis keys change accordingly: during a deployment, a request registered under the old key is not recognised as a duplicate by the new version for up to one TTL, and rate-limit windows start afresh. Set `scope` explicitly to keep a key stable
- `scripts/ddk.sh` installs the latest release (`vX.Y.Z` tag) by default instead of `main`. Set `DDK_REF=main` for the development line
- `ddk-projection-starter` changes its pending table: new columns `tenant_id`, `generation`, `claim_token` and `claimed_until`, and a primary key that includes the tenant. Because the primary key changes, a 0.8 table is upgraded by copying it into the new structure; existing marks are kept and count as having no tenant. With `initialize-schema=true` this happens at startup; with `initialize-schema=false` the application now fails at startup, printing the statements, when the table is missing or has the 0.8 structure. Upgrade with the 0.8 instances stopped, or rebuild the projection afterwards. An upgrade that stops halfway leaves `<table>_upgrade` behind, and the next start finishes it from there instead of leaving its marks unprocessed. Invalid settings (batch size below 1, non-positive durations, `retry-delay` above `max-retry-delay`) also fail at startup
- `ddk-projection-starter` creates an index on `available_at` for the sweep that picks due marks. With `initialize-schema=true` it is added to existing tables at startup; with `initialize-schema=false` a missing index is logged with the statement to run
- An idempotent integration event consumer that Spring proxies (for example because `handle` is `@Transactional`) is now recorded in the inbox under its own class name instead of the proxy's generated name. Events it handled before the upgrade and that are redelivered afterwards are handled again; override `consumerId()` with the old name (`Name$$SpringCGLIB$$0`) if that matters during the upgrade

### Fixed

- `OperatorContext` did not follow work onto other threads: `@Async` methods and tasks on Spring Boot's executors ran without an operator, so in a multi-tenant application their first statement failed. `ddk-core` now registers a Micrometer context-propagation accessor for it, so the `ContextPropagatingTaskDecorator` that `ddk-tracer-starter` registers carries the operator, tenant included, and restores the thread afterwards
- `@Idempotent` released registrations by key only: a call that ran past the TTL and then failed deleted the registration of a newer call with the same key, letting a third one in. Each registration now stores its own token and is removed with a compare-and-delete. A failure to remove it no longer replaces the method's exception
- Projections could keep a stale read-model entry with nothing left to correct it when two instances refreshed the same entry: a worker that read old state and finished last overwrote the newer state, and its acknowledgement removed nothing but also changed nothing. Marks also restarted at version 1 after deletion, so an old acknowledgement could delete a newer mark. Each mark now has a generation that is never reused and is claimed with a lease (`ddk.projection.lease-time`); a worker whose acknowledgement misses marks the entry again, so the read model always converges
- Idempotent integration event consumers in one group were told apart by their simple class name. Two classes with the same simple name shared one inbox identity, so the second skipped every event the first had handled. `IntegrationEventConsumer.consumerId()` now names a consumer explicitly, and identities that clash within a group fail at startup. Consumers without a clash keep their identity, so existing inbox records stay valid
- `scripts/ddk.sh` reused any earlier installation, so a changed `DDK_REF` or a newer commit on the same ref was never installed. It now records the ref, commit and version it installed in `~/.ddk/installed` and reuses an installation only when all three still match and the archetypes are in the local repository
- `DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS` did not cover Jackson 3 (`tools.jackson..`), the Jackson that Spring Boot 4 uses, so domain classes could depend on it unnoticed
- The four-layer template advised `AFTER_COMMIT` for every domain event subscriber, including work that must happen with the business change. The `ddk-add-domain-event` skill now chooses by purpose: `@EventListener` inside the transaction for integration messages and read-model marks, `AFTER_COMMIT` only for notifications that may be lost, an integration event for work that must complete

## [0.8.0] - 2026-10-10

The query side: read models that are refreshed from the write model and can be rebuilt, and row-level data scope on top of multi-tenancy. `ddk-mall` uses the first for order search.

### Added

- `ddk-mall` searches orders through a read model built on `ddk-projection-starter`: a wide table refreshed after each order change, rebuildable through an endpoint, and exposed as the `search_orders` MCP tool
- `ddk-projection-starter`: read models that follow the write model. `Projections.markDirty(name, id)` records a changed entry in the caller's transaction; after the commit a `Projection` bean regenerates that entry from the current state of the write model. Failures are retried with a growing delay, repeated marks are coalesced, a change during a refresh is never lost, and `Projections.rebuild(name)` refreshes everything. Works on MySQL, PostgreSQL and H2
- Row-level data scope in `ddk-mybatis-starter` (`ddk.mybatis.data-scope.*`): queries, updates and deletes on the listed tables are narrowed to what the current operator may see, by group column (`dept_id`), by creator column (`create_by`), or both. The application answers one question through a `DataScopeResolver` bean: what is this operator's `DataScope`. Like multi-tenancy it fails closed: a statement on a scoped table without an operator throws. Off by default

### Changed

- `MybatisPlusAutoConfiguration.mybatisPlusInterceptor` takes an additional `ObjectProvider<DataScopeResolver>`. It is a bean method that Spring calls; code that invoked it directly needs the extra argument

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

[Unreleased]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.12.2...HEAD
[0.12.2]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.12.1...v0.12.2
[0.12.1]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.12.0...v0.12.1
[0.12.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/poppycoderr/domain-driven-kit/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/poppycoderr/domain-driven-kit/releases/tag/v0.1.0
