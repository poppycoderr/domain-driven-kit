# DDK Roadmap

[简体中文](./ROADMAP.zh-CN.md)

<p align="center">
  <img src="./assets/diagrams/ddk-roadmap.svg" alt="DDK roadmap" />
</p>

This roadmap answers three questions: what DDK should become, in which order the work happens, and how the project stays both good-looking and genuinely useful. Every milestone must be releasable and demoable on its own, and everything marked done is backed by tests.

## Positioning

> **DDK makes DDD executable in Spring Boot projects: domain building blocks out of the box, architecture rules enforced in CI, middleware integrated through domain semantics, and the same guardrails for humans and AI coding agents.**

Three observations decide what DDK does and does not do:

1. **Spring already covers modularity and event registries.** Spring Modulith 2 verifies module boundaries and ships a persistent event publication registry; jMolecules provides DDD concept annotations. DDK interoperates with them instead of rebuilding them.
2. **The mainstream stack in China lacks a DDD layer.** MyBatis-Plus, Redis, RocketMQ, Seata, XXL-Job and Nacos are an extremely common combination, yet almost nothing ties them to aggregates, repositories and domain events. This is where DDK is most distinctive.
3. **AI coding agents need executable architecture constraints.** Tools like Claude Code and Codex write code fast and blur layers just as fast. A project with a predictable structure and rules that fail tests is naturally better suited to agent collaboration. DDK already has ArchGuard; turning it into the agent's feedback loop is the obvious next step.

```text
             ┌───────────────────────────── your business code ─────────────────────┐
             │   adapter  ──►  application  ──►  domain  ◄──  infrastructure        │
             └──────┬───────────────┬──────────────┬───────────────┬────────────────┘
                    │               │              │               │
  Guardrails   ArchGuard rules · AGENTS.md / Skills · generated projects ship architecture tests
  Domain       ddk-core: aggregates · value objects · domain events · specifications · repositories
  Reliability  outbox · idempotency · distributed locks · rate limits   ← keyed by aggregate / command
  Integration  MyBatis-Plus · Redis · RocketMQ/Kafka · XXL-Job · Elasticsearch · Seata
  AI           Spring AI · MCP: application services as tools, under the same validation and permissions
  Platform     Spring Boot 4 · Jackson 3 · JSpecify · virtual threads · OpenTelemetry
```

### Integration principles

Middleware integration is the fastest way to turn a toolkit into a kitchen sink. DDK only integrates where **domain semantics add value**:

| A thin wrapper (avoid) | What DDK should provide |
|---|---|
| A `RedisUtil` that forwards every `RedisTemplate` method | `@AggregateLock` keyed by aggregate ID, with lock failures mapped to business errors |
| Another `send()` wrapper around RocketMQ | Domain events delivered reliably through an outbox to RocketMQ / Kafka, with idempotent consumers |
| Registering the Elasticsearch client as a bean | Read-model projections driven by domain events, rebuildable by replay |
| Re-wrapping XXL-Job annotations | Scheduled jobs that only call application services, enforced by architecture rules |

Every starter must meet the same **starter contract** before it is merged:

- Configuration lives under `ddk.*`, and risky features are disabled by default
- User-defined beans win (`@ConditionalOnMissingBean`)
- `ApplicationContextRunner` wiring tests, plus Testcontainers integration tests for external middleware
- A dedicated docs page: what it does, where it does not fit, known issues
- Used by at least one example or archetype

## Done: the foundation

Former Phases 1–5 are complete and form the starting point:

- **Engineering baseline**: a real BOM, CI, Spotless, per-module JaCoCo minimums (70% lines, 50% branches), 213 tests
- **Domain model**: `Identifier`, `ValueObject`, `Entity`, `AggregateRoot`, `DomainEvent`, `Specification`, with a framework-free domain layer enforced by tests
- **Mapping and repositories**: missing or duplicate mappers fail at startup, the generic repository supports optimistic locking, H2 integration tests
- **Starter normalization**: nine starters (Web, MyBatis, Redis, two-level cache, multiple data sources, tracing, Seata, domain events, ArchGuard) under a unified `ddk.*` configuration
- **Archetypes and examples**: three- and four-layer Maven archetypes with generated-project integration tests, plus a runnable user registration example

## v0.1 · Modern baseline and first release

**Goal: anyone can use DDK within five minutes.** Maven Central is deferred; first make "clone → one install command → generate a project" smooth.

Platform upgrade (Spring Boot 3.4 left OSS support in December 2025, so this comes first):

- [x] Upgrade to Spring Boot 4.1 / Spring Framework 7, adapting to Jackson 3 and Jakarta EE 11
- [x] Switch MyBatis-Plus to `mybatis-plus-spring-boot4-starter`
- [x] Assess Seata's Boot 4 support: Seata 2.6 auto-configuration only references Boot APIs still present in 4, and DDK's wiring tests pass, so the starter stays; Seata's runtime is not yet verified on Boot 4
- [x] Keep Java 21 as the baseline and add Java 25 to the CI matrix
- [ ] Annotate public APIs with JSpecify nullness annotations
- [x] Build the tracer starter on `spring-boot-starter-opentelemetry` instead of the whole actuator, keeping only DDK-specific features such as the trace ID response header
- [ ] Use Spring Framework 7's built-in `@Retryable` / `@ConcurrencyLimit` instead of adding a retry library

Release readiness:

- [ ] Version `0.1.0`, with a documented 0.x policy: minor versions may break, and every break is in the release notes
- [ ] Separate public and internal packages, with internals under `internal` and guarded by ArchUnit
- [ ] Javadoc for public APIs, `CHANGELOG.md`, and generated GitHub release notes
- [ ] CI creates a GitHub Release on tag: release notes, source archive and module jars
- [ ] One script that installs DDK locally and generates a project, with the README quick start written around it

## v0.2 · AI collaboration

**Goal: a DDK-generated project stays clean when Claude Code or Codex writes the code.** This is DDK's most visible differentiator.

Agent guardrails:

- [ ] Archetypes generate `AGENTS.md` and `CLAUDE.md`: layer responsibilities, naming, prohibitions, and the commands that must pass before committing
- [ ] Claude Code Skills and Codex prompt templates: add an aggregate, a use case, a domain event, a starter
- [ ] ArchGuard emits machine-readable violations (which rule, which class, how to fix) that agents can use to self-correct
- [ ] A docs topic on developing DDK projects with AI agents, with real session recordings

AI capabilities (on Spring AI 2.0):

- [ ] `ddk-ai-starter`: `ChatClient` defaults, structured output mapped straight to value objects, token usage in Micrometer
- [ ] `ddk-mcp-starter`: expose application-service commands and queries as MCP tools, reusing Bean Validation, permissions and auditing; repositories are never exposed directly
- [ ] Example: an agent registering and disabling users through MCP in the user example

## v0.3 · Reliable domain events

**Goal: domain events grow from in-process notifications into reliable integration events.**

- [ ] Transactional outbox: events are written in the aggregate's transaction and relayed asynchronously, with retries and a dead letter
- [ ] Delivery adapters: RocketMQ, Kafka, RabbitMQ
- [ ] Idempotent consumers: an inbox table keyed by message ID, `@IdempotentConsumer`
- [ ] Interoperate with the Spring Modulith event publication registry and jMolecules `@Externalized` rather than duplicating them
- [ ] Event contracts: versions, schema evolution rules, per-event serialization settings

## v0.4 · Middleware and library integrations

**Goal: cover the most common middleware in the mainstream stack, all following the integration principles and the starter contract.**

| Area | Integration | Domain semantics | Priority |
|---|---|---|---|
| Concurrency control | Redisson | `@AggregateLock`, per-user / per-tenant rate limits, `@Idempotent` duplicate-submit protection | High |
| Scheduling | XXL-Job, ShedLock | Jobs only call application services and never run twice across instances | High |
| Schema migration | Flyway | Archetypes ship migrations; examples stop relying on `schema.sql` | High |
| API docs | springdoc-openapi | Unwrap `ApiResponse<T>` automatically and document error codes | High |
| Read models | Elasticsearch | Projections driven by domain events, with rebuilds | Medium |
| Cross-cutting | MyBatis-Plus plugins | Multi-tenancy, data permissions, audit fields, soft delete, driven by one operator context | Medium |
| Config and discovery | Nacos | Refreshable `ddk.*` properties, kept as an optional dependency | Medium |
| Traffic control | Sentinel | Circuit breaking at the application-service level, with fallbacks mapped to error codes | Low |
| Object storage | S3-compatible (MinIO / OSS) | File references as value objects; the domain never touches the SDK | Low |

Testing:

- [ ] `ddk-test`: aggregate assertions (which events were raised, whether invariants hold) and Testcontainers presets (MySQL, Redis, RocketMQ)

## v1.0 · Production ready

**Goal: safe to use in production projects.**

- [ ] `ddk-mall` reference application: order, inventory and payment bounded contexts covering outbox, MQ, caching, read models and distributed locks; `docker compose up` starts it with an OpenTelemetry dashboard
- [ ] GraalVM `RuntimeHints` for every starter, with a native image build of the example
- [ ] Verified behavior on virtual threads (locks, ThreadLocal context propagation)
- [ ] Public API freeze, strict semantic versioning from here on
- [ ] Publish to Maven Central once stars and usage feedback grow noticeably, using the GitHub-backed `io.github.poppycoderr` namespace so no owned domain is needed
- [ ] Bilingual documentation site

## Ongoing: good-looking and useful

Stars come from two things: **the first screen explains the value**, and **it actually runs within five minutes**. This track is not tied to one milestone; every release moves it forward.

Good-looking:

- [ ] README first screen: a one-line value proposition, badges (CI, coverage, latest release, license), and a 20-second GIF of "generate project → break layering → test fails → fix"
- [ ] A comparison with COLA, Spring Modulith and jMolecules that explains where each fits, without disparaging anyone
- [ ] A social preview image and GitHub topics (`ddd`, `spring-boot`, `archunit`, `mybatis-plus`, `ai-agents`)
- [ ] Diagrams in one visual system, with dark mode

Useful:

- [ ] One command to start a project: `jbang ddk@poppycoderr new my-app`, choosing three or four layers and the starters needed
- [ ] An online project generator on the docs site, similar to Spring Initializr
- [ ] A 30-second getting-started snippet on every starter's docs page
- [ ] Clear error messages for common mistakes, with troubleshooting in the docs

Community:

- [ ] Issue and PR templates, `good first issue` labels, GitHub Discussions
- [ ] A codesphere article explaining the design trade-offs of every release
- [ ] English and Chinese READMEs maintained in sync, promoted to both communities

## Non-goals

- No home-grown ORM, message broker or service registry; only integrations with domain meaning
- No reimplementation of what Spring Modulith already provides; interoperate first
- No event sourcing: domain events are for integration and decoupling, not for rebuilding state
- No low-code or code generation platform: only skeletons are generated, and business code is written by people (or agents) within the guardrails
- No AI features for the sake of hype: AI capabilities go through application services, under the same validation, permissions and architecture rules
