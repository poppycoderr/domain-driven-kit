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

## Rolling back an upgrade

Going back to the previous version is a matter of reverting the version in your build when the upgrade did not change stored data. Each section of the [upgrade guide](https://domaindrivenkit.com/ddk/upgrade) says whether it does and, if so, how to roll back. Data that DDK stores or sends:

- the projection mark table and the processed message table;
- Redis keys of locks, idempotency, rate limits, job locks and the cache;
- integration event headers.

Rehearse a rollback on a copy before relying on it in production, the same way as the upgrade.
