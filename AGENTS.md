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
- **Java layering in examples and archetypes**: `*Request` / `*Response` in their own files with `static from(...)`; rich domain model with no public setters; explicit `@PathVariable("id")` and `@RequestParam(value = "...")`
- **Diagrams**: the Chinese SVGs in `assets/diagrams` are the source; regenerate English variants with `python3 scripts/translate-diagrams.py assets/diagrams`
- **Changelog**: add user-visible changes to the `[Unreleased]` section of `CHANGELOG.md`

## Git mode

- This repository uses **pull request mode** from the shared rules below. `main` is protected and merges are rebase-only.
- Run `mvn verify` before pushing a branch; run `mvn clean verify` when nullness findings are involved.

## Documentation

User documentation lives on the codesphere site (`poppycoder.netlify.app/ddk/`, sources in the `poppycoderr/codesphere` repository). When a change alters behavior, configuration or public API, update the matching page there as well as the module README.

<!-- shared-agent-rules:start v1 — keep this block identical (per language) in codesphere, codesphere-labs, domain-driven-kit and grounded-access -->
## Shared rules

These rules are shared by the maintainer's repositories: codesphere, codesphere-labs, domain-driven-kit and grounded-access. The project-specific sections of this file add to them; where the two conflict, the project-specific section wins.

### Working language

- Talk to the maintainer in Chinese unless English is needed for accuracy (error messages, identifiers, quotes).
- Commits, PR titles, code identifiers and logs are in English. Documentation follows the language of the repository.

### Working efficiently

- Read the relevant code and docs before changing anything, and plan a multi-file change once instead of asking after every step.
- Search narrowly: grep specific paths, read only the ranges you need, and don't re-read a file you have just edited.
- Edit with targeted replacements rather than rewriting whole files, and don't paste file contents back into the conversation.
- Run independent commands together. Run long builds or experiments in the background instead of polling them.
- Reuse decisions that are already settled (known errors, permission flows, tool choices) instead of explaining them again.
- When done, report briefly: what changed, how it was verified, the commit hash and the push result. Don't narrate your reasoning unless asked.
- Fix and retry problems yourself. Stop and ask only before risky or irreversible actions: deleting data, force-pushing, rewriting history, releases or anything touching production.

### Code

- Follow the existing style and layout. Don't add a framework or toolchain without a stated reason, and don't touch files unrelated to the task.
- Comments: a class-level comment says what a type is for; method comments only for pitfalls such as concurrency, ordering or non-obvious algorithms; no line-by-line comments and no TODO placeholders.
- Java: 4-space indent, lines up to about 150 columns, one record component per line.
- Check library and API documentation instead of relying on memory, and pin dependency and image versions.
- Tests cover core logic and failure paths rather than a coverage number. Never skip, disable or weaken a check to get a green build.
- Only report test, benchmark or measurement results you actually ran, and keep the evidence where the project says it belongs.

### Commits

- Conventional Commits in English: `<type>(<optional scope>): <subject>` with a lowercase imperative subject. One line, no body. One logical change per commit.
- No emoji, no AI attribution and no `Co-Authored-By` trailers.
- Before committing, review the staged diff: no scratch notes, build output, secrets or unrelated changes. Never `git add -f` an ignored path.
- Commit and push only when asked. Never force-push or rewrite pushed history without explicit consent.
- If a push fails because authentication expired, run `gh auth login`, continue, and report only the result.

### Branches and pull requests

Each repository uses one of two modes. The project-specific section states which.

- **Direct mode** (codesphere, codesphere-labs): commit to the default branch. No feature branches and no pull requests.
- **Pull request mode** (domain-driven-kit, grounded-access):
  - Never commit directly to `main`. Branch from the latest `main` as `<type>/<short-description>`, for example `feat/policy-compiler` or `docs/threat-model`. Don't use tool-specific prefixes such as `codex/` or `claude/`.
  - A branch contains only its own change. Resolve conflicts by rebasing onto `main`; merges are rebase-only. Don't merge a PR, publish a release or run a deployment without explicit instruction.
  - **PR title:** English, same format as a commit.
  - **PR description:** bilingual. Write the full English version first, then a horizontal rule (`---`), then a Chinese version with the same content. Describe the change itself: background, scope, contract or data model changes, key design decisions. Leave out sections such as testing, risks or deployment (CI shows test results), and leave out AI attribution footers. A first PR for a feature describes it as new rather than listing bugs fixed during development.
  - **PR diagrams:** write them with the `show-me` skill. Pick the smallest view for each point (pseudocode for logic, a call tree for control flow, a shallow file tree for layout, `mermaid` for interaction or data flow, a `diff` for what changes), usually 1–3 per PR, as `text`, `diff` or `mermaid` code blocks placed next to the text they support. Put them in the English section; the Chinese section refers to them.

### CI and automation

- Checks triggered by pushes and pull requests run on their own; don't cancel, rerun or disable them.
- Dispatch a workflow manually only when asked, then return the run link. Don't poll it unless asked to wait or investigate.

### Scratch work and secrets

- Plans, reviews, notes and visualizations go in the repository's ignored scratch directory (`drafts/` in codesphere, `.agentdocs/` elsewhere). Don't put them in tracked docs, and don't change ignore rules to commit them.
- No passwords, tokens, internal hostnames, personal absolute paths or real business data in code, logs, evidence or docs. Use obvious demo values such as `example_password`.
<!-- shared-agent-rules:end -->
