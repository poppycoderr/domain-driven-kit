# AGENTS.md

Guidance for AI coding agents (Claude Code, Codex and others) contributing to Domain Driven Kit. Human contributors follow the same rules.

## Build and verify

```bash
mvn -B -ntp install -Parchetype-it,release   # what CI runs: tests, archetype ITs, Javadoc
mvn verify                                    # quick local gate
mvn spotless:apply                            # fix formatting findings
```

`mvn verify` fails on any of these, and none of them may be weakened to get a green build:

- Spotless: unused imports, trailing whitespace, tabs, missing final newline
- JaCoCo: at least 70% line and 50% branch coverage per module
- NullAway: main code is `@NullMarked`; mark anything that may be `null` with JSpecify `@Nullable`. NullAway only runs on modules that recompile, so run `mvn clean compile` when checking nullness findings
- ArchUnit: `DomainPackagePurityTest` in `ddk-core`, and the architecture tests in the example and generated projects

Integration tests for the cache starter use Testcontainers and need a running Docker daemon.

## Layout

| Module | Role |
|---|---|
| `ddk-core` | Domain model primitives, repository contract, pagination, mappers, errors, `ApiResponse`. `com.ddk.core.domain` depends only on the JDK and JSpecify |
| `ddk-mybatis` | MyBatis-Plus implementation of the repository contract |
| `ddk-starters/*` | Spring Boot starters, one concern each |
| `ddk-dependencies` | BOM; does not inherit the parent pom |
| `ddk-archetypes/*` | Three- and four-layer project templates (Velocity; Markdown headings use `$h2`) |
| `ddk-examples/*` | Runnable reference applications |
| `scripts/` | `ddk.sh` installer and the diagram translation script |

## Conventions

- **Starter contract**: configuration under `ddk.*`, risky features off by default, `@ConditionalOnMissingBean` so user beans win, `ApplicationContextRunner` tests, Testcontainers for real middleware
- **Public vs internal**: implementation classes that only auto-configuration instantiates go in `com.ddk.<name>.starter.internal`; types users inject, extend, implement or configure stay public
- **Comments**: class-level Javadoc explaining what the type is for; method comments only for traps (concurrency, ordering, algorithms); no line-by-line comments or TODO placeholders
- **Formatting**: 4-space indent, lines up to about 150 columns, one record component per line
- **Java layering in examples and archetypes**: `*Request` / `*Response` in their own files with `static from(...)`; rich domain model with no public setters; explicit `@PathVariable("id")` and `@RequestParam(value = "...")`
- **Diagrams**: the Chinese SVGs in `assets/diagrams` are the source; regenerate English variants with `python3 scripts/translate-diagrams.py assets/diagrams`
- **Changelog**: add user-visible changes to the `[Unreleased]` section of `CHANGELOG.md`

## Git and pull requests

- Work on a branch and open a PR against `main`; `main` is protected and merges are rebase-only
- Commits: Conventional Commits, English, one-line subject, one logical change per commit
- PR titles in English; PR descriptions describe the feature and may include small text diagrams

## Documentation

User documentation lives on the codesphere site (`poppycoder.netlify.app/ddk/`, sources in the `poppycoderr/codesphere` repository). When a change alters behavior, configuration or public API, update the matching page there as well as the module README.
