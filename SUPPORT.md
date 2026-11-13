# Support

[简体中文](./SUPPORT.zh-CN.md)

Who maintains DDK, which versions get fixes, what it is tested against, and how to go back after an upgrade. What stays compatible between releases is in [COMPATIBILITY.md](./COMPATIBILITY.md).

## Maintenance

DDK is maintained by one person, [@poppycoderr](https://github.com/poppycoderr), with AI coding agents working under the rules in [`AGENTS.md`](./AGENTS.md). Questions and bugs go to [GitHub Issues](https://github.com/poppycoderr/domain-driven-kit/issues). Answers are best effort; there is no guaranteed response time and no commercial support. Security issues are reported privately, as described in [SECURITY.md](./SECURITY.md).

## Versions that get fixes

The newest minor release line gets fixes, as patch releases. Today that is **1.0.x**. When a major release comes out, the last minor line before it still gets security fixes for three months, so that a security fix never requires an upgrade with breaking changes: **0.12.x** gets them until 2027-02-13. Other lines get no fixes, so a fix needs an upgrade to the newest line, following the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) one minor step at a time.

## What DDK is tested against

Every change runs the full test suite in CI on these versions. Other versions may work but are not tested.

| | Tested |
|---|---|
| JDK | 21 and 25 |
| Spring Boot | 4.1 (built against 4.1.2) |
| Databases | H2, MySQL 8.4, PostgreSQL 17 |
| Redis | 7 |
| RocketMQ | 5.5 (server 5.5.0, client 5.5.1) |
| Elasticsearch | 9.4 (in `ddk-mall`) |
| Libraries DDK builds on | MyBatis-Plus 3.5.18.1, Redisson 4.8.0, ShedLock 7.11.0, Spring Modulith 2.1.2, Spring AI 2.0.2, springdoc 3.2.0 |

How thoroughly each module is verified, and its known limits, is in the [support matrix](./ROADMAP.md#support-status). Modules marked wiring only are experimental: see [COMPATIBILITY.md](./COMPATIBILITY.md#what-is-not-stable). The semantics on virtual threads are on the [virtual threads page](https://domaindrivenkit.com/ddk/core/virtual-threads).

## GraalVM native images

Native images are verified module by module, not promised for all of DDK at once. CI builds `ddk-example-user` with GraalVM CE 25.0.2 (`mvn -Pnative native:compile` from Spring Boot's parent) and runs `scripts/native-smoke.sh` against the executable. It checks the example's success paths and the listed error paths: a conditional query and an insert with a snowflake ID, a read by ID, paging filtered two ways with its total, an update, a disable and a delete, Bean Validation (`VALIDATION_ERROR`), a business error, a domain rule and a missing entity, an MCP tool call, and two domain event listeners after commit. It does not check optimistic-lock conflicts, rolled-back transactions or anything the example does not use, such as tenants, data scope, the outbox or Redis.

CI also builds `ddk-example-inventory` against a Redis service and runs `scripts/native-smoke-inventory.sh`: a read through the two-level cache and its eviction by a write, a reservation under an aggregate lock with duplicate-submit protection and its duplicate rejected, a domain rule and Bean Validation, a rate limit reached, a projection refreshed after commit, and a listener that runs through Spring Modulith's JDBC publication registry with `IdempotentConsumer`. It runs one instance, so it does not check locks or cache invalidation across instances, a lock that times out, retries of failed refreshes or publications, or tenants.

| Module | Native image |
|---|---|
| `ddk-core`, `ddk-mybatis`, `ddk-mybatis-starter`, `ddk-web-starter` | Verified in CI |
| `ddk-event-starter` | Verified in CI for domain events inside the application, and for listeners run through Spring Modulith's JDBC publication registry with `IdempotentConsumer`. Externalizing events to RocketMQ is not verified |
| `ddk-concurrency-starter` | Verified in CI against Redis: aggregate locks, duplicate-submit protection and rate limits. The parameter types that key expressions read are registered for reflection automatically |
| `ddk-redis-starter`, `ddk-cache-starter` | Verified in CI: two-level cache reads, eviction, the invalidation message it publishes. The types an application stores in Redis are its own to register for reflection, for example with `@RegisterReflectionForBinding` |
| `ddk-projection-starter` | Verified in CI: a mark written with the transaction and refreshed after commit, on H2 |
| `ddk-mcp-starter` | Verified in CI: synchronous server over streamable HTTP, tool calls |
| Archetypes | A project generated from 0.10.0 compiled and started once by hand; not in CI |
| Every other module | Not verified. It may work; nothing tests it |

`ddk-mybatis-starter` registers the reflection that MyBatis-Plus and the default ID generator need on a native image, `ddk-cache-starter` the Caffeine classes of its local level, and `ddk-concurrency-starter` the parameters its key expressions read, so applications add nothing for them. A module moves up this table when CI exercises it.

## Rolling back an upgrade

Going back to the previous version is a matter of reverting the version in your build when the upgrade did not change stored data. Each section of the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) says whether it does and, if so, how to roll back. The data that DDK stores or sends is listed under [Data and wire formats](./COMPATIBILITY.md#data-and-wire-formats).

Rehearse a rollback on a copy before relying on it in production, the same way as the upgrade.
