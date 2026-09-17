# DDK Roadmap

This roadmap keeps the repository honest: what already works, what was recently fixed, and what should be improved next.

<p align="center">
  <img src="./assets/diagrams/ddk-roadmap.svg" alt="DDK roadmap" />
</p>

## Done Recently

- Shipped `com.ddk.core.domain`: `Identifier`, `ValueObject`, `Entity`, `AggregateRoot`, `DomainEvent`, `AbstractDomainEvent`, `DomainEventPublisher`, `Specification`
- Enforced the "domain depends on no framework" rule with `DomainPackagePurityTest` instead of leaving it in prose
- Fixed `MapperProvider` key collisions by switching from simple names to fully qualified names
- Replaced the `DefaultMapper` fallback with `MissingMapperException`, so a forgotten mapper fails at lookup instead of producing an empty object and a distant `ClassCastException`
- Made duplicate mapper registration and non-`ObjectMapper` beans fail at startup
- Reordered `GenericRepository<ID, E>` to `<E, ID>` to match its javadoc and Spring Data conventions
- Rewrote the 4-layer archetype: a real aggregate root, value objects, domain events, explicit Entity↔PO converters and an executable `ArchitectureTest`
- Added `DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS` and `DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS` to `CommonArchRules`
- Grew the test suite from 14 to 96 tests; `ddk-core` and `ddk-mybatis` went from zero coverage to covered
- Filled `ddk-dependencies` as a real BOM that deliberately does not inherit the parent pom
- Added CI with Maven verify and fixed the reactor build failures that had been silently skipping 8 modules
- Fixed `ddk-web-starter` global exception handling by registering `BaseExceptionHandler` as `@RestControllerAdvice`
- Added a default max page size to `PageQuery` and fixed sorting so `PageQuery.addSort()` reaches MyBatis-Plus
- Redrew every architecture diagram in one visual system, with light and dark variants

## Phase 1 - Engineering Baseline

Goal: make the project easier to build, review and maintain.

- [x] Fill `ddk-dependencies` as a real BOM
- [x] Add JaCoCo reporting
- [x] Add Spotless checks for unused imports and whitespace, without a whole-file formatter that fights the 150-column style
- [x] Enforce per-module JaCoCo minimums (70% lines, 50% branches) as a regression floor
- [x] Generate `spring-boot-configuration-processor` metadata for every starter from the root pom
- [x] Remove generated `target/` artifacts and classpath-sensitive resources from library modules

## Phase 2 - Core DDD Model

Goal: make `ddk-core` useful beyond response and pagination helpers.

- [x] Add `Identifier`
- [x] Add `ValueObject`
- [x] Add `Entity`
- [x] Add `AggregateRoot`
- [x] Add `DomainEvent` and `DomainEventPublisher`
- [x] Add `Specification`
- [x] Add focused unit tests for invariants and equality behavior
- [x] Provide a Spring implementation of `DomainEventPublisher` in a starter, rather than leaving it in the archetype

## Phase 3 - Mapper and Repository Safety

Goal: fail early when mapping is unsafe.

- [x] Replace the `DefaultMapper` fallback with explicit failure for missing Entity ↔ PO mappers
- [x] Use fully qualified class names for mapper keys to avoid same-simple-name collisions
- [x] Add tests for missing mapper, duplicate mapper and list mapping
- [x] Review `GenericRepository` generic order and document the compatibility decision
- [x] Add `ddk-mybatis` integration tests with H2
- [x] Map `AggregateRoot.version` to MyBatis-Plus optimistic locking in the generic repository, not only in the archetype

## Phase 4 - Starter Normalization

Goal: make every starter predictable in production projects.

- [x] Give Web and MyBatis a dedicated `ddk.*` configuration prefix
- [x] Ship configuration metadata (generated from `@ConfigurationProperties` javadoc by the annotation processor, so no hand-written file is needed)
- [x] Replace hard-coded CORS defaults with configurable properties, disabled by default
- [x] Remove `logback-spring.xml` from `ddk-web-starter`
- [x] Contribute Jackson defaults through a builder customizer instead of replacing the `ObjectMapper` bean
- [x] Stop the catch-all exception handler from turning 405/415 responses into 500s
- [x] Add tests for the Web and MyBatis starters
- [x] Give Redis a dedicated `ddk.*` prefix
- [x] Give Cache a dedicated `ddk.*` prefix
- [x] Give DB, Tracer and Seata a dedicated `ddk.*` prefix
- [x] Key DB starter sources by name, bind pool properties and fail fast on an ambiguous primary
- [x] Return the trace ID in an HTTP response header from the tracer starter
- [x] Propagate the Seata XID on outbound `RestClient` / `RestTemplate` calls
- [x] Restrict polymorphic deserialization in `ddk-redis-starter`
- [x] Rewrite `ddk-cache-starter` around a real multi-level cache design
- [x] Add `ApplicationContextRunner` tests for the Redis starter

## Phase 5 - Archetypes and Examples

Goal: let users try DDK without reading every document first.

- [x] Make the 4-layer skeleton a correct, readable reference implementation (now `ddk-examples/ddk-example-user`)
- [x] Ship an executable `ArchitectureTest` in the skeleton
- [x] Convert `ddk-archetypes` into real Maven archetypes, with integration tests that build the generated projects
- [x] Bring the 3-layer skeleton up to the same standard, with its own architecture rule
- [x] Add a runnable example with schema, seed data and smoke test commands
- [ ] Keep examples aligned with codesphere documentation

## Phase 6 - Release Readiness

Goal: prepare for a public artifact release only after the API settles.

- [ ] Stabilize package names and public contracts
- [ ] Add a semantic versioning policy
- [ ] Add release notes
- [ ] Add javadocs for public APIs
- [ ] Decide whether to publish to Maven Central

## Non-Goals For Now

- No Maven Central publishing until examples and tests are reliable
- No attempt to compete with full application frameworks
- No event sourcing; domain events are for integration and decoupling, not for rebuilding state
- No automatic Specification-to-SQL translation until there is a real need
- No hidden magic in domain model classes; domain code should remain plain Java first
