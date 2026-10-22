# Support

[简体中文](./SUPPORT.zh-CN.md)

Who maintains DDK, which versions get fixes, what it is tested against, and how to go back after an upgrade. What stays compatible between releases is in [COMPATIBILITY.md](./COMPATIBILITY.md).

## Maintenance

DDK is maintained by one person, [@poppycoderr](https://github.com/poppycoderr), with AI coding agents working under the rules in [`AGENTS.md`](./AGENTS.md). Questions and bugs go to [GitHub Issues](https://github.com/poppycoderr/domain-driven-kit/issues). Answers are best effort; there is no guaranteed response time and no commercial support. Security issues are reported privately, as described in [SECURITY.md](./SECURITY.md).

## Versions that get fixes

Only the newest minor release line gets fixes, as patch releases. Today that is **0.10.x**. An older line gets no fixes, so a fix needs an upgrade to the newest line, following the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) one minor step at a time.

## What DDK is tested against

Every change runs the full test suite in CI on these versions. Other versions may work but are not tested.

| | Tested |
|---|---|
| JDK | 21 and 25 |
| Spring Boot | 4.1 (built against 4.1.1) |
| Databases | H2, MySQL 8.4, PostgreSQL 17 |
| Redis | 7 |
| RocketMQ | 5.5 (server 5.5.0, client 5.5.1) |
| Elasticsearch | 9.4 (in `ddk-mall`) |
| Libraries DDK builds on | MyBatis-Plus 3.5.17, Redisson 4.7.0, ShedLock 7.10.1, Spring Modulith 2.1.1, Spring AI 2.0.1, springdoc 3.1.1. Seata 2.6.0 is compiled against but its runtime is not tested |

How thoroughly each module is verified, and its known limits, is in the [support matrix](./ROADMAP.md#support-status). Modules marked wiring only are experimental: see [COMPATIBILITY.md](./COMPATIBILITY.md#what-is-not-stable). The semantics on virtual threads are on the [virtual threads page](https://domaindrivenkit.com/ddk/core/virtual-threads).

## GraalVM native images

Native images are verified module by module, not promised for all of DDK at once. CI builds `ddk-example-user` with GraalVM CE 25.0.2 (`mvn -Pnative native:compile` from Spring Boot's parent) and runs `scripts/native-smoke.sh` against the executable: conditional queries, inserts with snowflake IDs, paging, optimistic locking, business and validation errors, an MCP tool call, and domain event listeners after commit.

| Module | Native image |
|---|---|
| `ddk-core`, `ddk-mybatis`, `ddk-mybatis-starter`, `ddk-web-starter` | Verified in CI |
| `ddk-event-starter` | Verified in CI for domain events inside the application; the outbox and RocketMQ delivery are not verified |
| `ddk-mcp-starter` | Verified in CI: synchronous server over streamable HTTP, tool calls |
| Archetypes | A project generated from 0.10.0 compiled and started once by hand; not in CI |
| Every other module | Not verified. It may work; nothing tests it |

`ddk-mybatis-starter` registers the reflection that MyBatis-Plus and the default ID generator need on a native image, so applications add nothing for it. A module moves up this table when CI exercises it.

## Rolling back an upgrade

Going back to the previous version is a matter of reverting the version in your build when the upgrade did not change stored data. Each section of the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) says whether it does and, if so, how to roll back. Data that DDK stores or sends:

- the projection mark table and the processed message table;
- Redis keys of locks, idempotency, rate limits, job locks and the cache;
- integration event headers.

Rehearse a rollback on a copy before relying on it in production, the same way as the upgrade.
