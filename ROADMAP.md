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
- [ ] Add Spotless or Checkstyle for formatting
- [ ] Add JaCoCo thresholds now that core tests are in place
- [ ] Add `spring-boot-configuration-processor` metadata for every starter
- [ ] Remove generated `target/` artifacts and classpath-sensitive resources from library modules

## Phase 2 - Core DDD Model

Goal: make `ddk-core` useful beyond response and pagination helpers.

- [x] Add `Identifier`
- [x] Add `ValueObject`
- [x] Add `Entity`
- [x] Add `AggregateRoot`
- [x] Add `DomainEvent` and `DomainEventPublisher`
- [x] Add `Specification`
- [x] Add focused unit tests for invariants and equality behavior
- [ ] Provide a Spring implementation of `DomainEventPublisher` in a starter, rather than leaving it in the archetype

## Phase 3 - Mapper and Repository Safety

Goal: fail early when mapping is unsafe.

- [x] Replace the `DefaultMapper` fallback with explicit failure for missing Entity ↔ PO mappers
- [x] Use fully qualified class names for mapper keys to avoid same-simple-name collisions
- [x] Add tests for missing mapper, duplicate mapper and list mapping
- [x] Review `GenericRepository` generic order and document the compatibility decision
- [ ] Add `ddk-mybatis` integration tests with H2
- [ ] Map `AggregateRoot.version` to MyBatis-Plus optimistic locking in the generic repository, not only in the archetype

## Phase 4 - Starter Normalization

Goal: make every starter predictable in production projects.

- [ ] Give every starter a dedicated `ddk.*` configuration prefix
- [ ] Add `additional-spring-configuration-metadata.json`
- [ ] Replace hard-coded CORS defaults with configurable properties
- [ ] Remove `logback-spring.xml` from `ddk-web-starter` or move it to examples
- [ ] Rewrite `ddk-cache-starter` around a real multi-level cache design
- [ ] Add `ApplicationContextRunner` tests for Web, Redis and MyBatis starters (Cache, DB, Tracer, Seata and ArchGuard already have them)

## Phase 5 - Archetypes and Examples

Goal: let users try DDK without reading every document first.

- [x] Make the 4-layer skeleton a correct, readable reference implementation
- [x] Ship an executable `ArchitectureTest` in the skeleton
- [ ] Convert `ddk-archetypes` into real Maven archetypes
- [ ] Bring the 3-layer skeleton up to the same standard; it is currently only an `Application` class
- [ ] Add a runnable example with schema, seed data and smoke test commands
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
