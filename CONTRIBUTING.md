# Contributing to Domain Driven Kit

Thanks for helping. This page is the short version; [AGENTS.md](./AGENTS.md) holds the full rules, and they apply to people and coding agents alike.

## Where to start

- **Questions** go to an issue with the `question` label. Check the [user guide](https://domaindrivenkit.com/ddk/) and the module README first.
- **Bugs and ideas** go to an [issue](https://github.com/poppycoderr/domain-driven-kit/issues/new/choose). The templates ask for what we need to reproduce or weigh it.
- **Security problems** never go to a public issue. Follow [SECURITY.md](./SECURITY.md).
- **First contribution?** Issues labelled [`good first issue`](https://github.com/poppycoderr/domain-driven-kit/labels/good%20first%20issue) are small, self-contained and say which files to touch and how to check the result. Comment on the issue before you start so two people don't do the same work.

## Build

You need JDK 21 or newer, Maven 3.9 and, for the integration tests, a running Docker daemon.

```bash
mvn verify               # the local gate
mvn spotless:apply       # fix formatting findings
```

`mvn verify` fails on formatting, coverage below 70% lines / 50% branches per module, nullness findings (NullAway), architecture rules (ArchUnit) and failing tests. None of these may be weakened to get a green build. When a nullness finding looks wrong, run `mvn clean compile`: NullAway only checks modules that recompile.

The full set of CI checks, including the generated projects and the API compatibility check, is listed in [AGENTS.md](./AGENTS.md#build-and-verify).

## Making a change

1. Branch from the latest `main` as `<type>/<short-description>`, for example `fix/cache-ttl-jitter` or `docs/web-starter-readme`.
2. Keep one logical change per branch, and follow the style of the code around it.
3. Add tests for the behavior and the failure paths you change.
4. Add a line to the `[Unreleased]` section of [CHANGELOG.md](./CHANGELOG.md) if users can notice the change.
5. Update the module README when behavior, configuration or public API changes.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) in English, on one line with a lowercase imperative subject:

```text
fix(cache): keep the local copy when the redis eviction fails
docs(web): add a readme for the web starter
```

Pull requests are merged by rebase, so the commits on your branch end up on `main` as they are.

## What needs extra care

Some things outlive one deployment, so changing them breaks running systems even when the Java API stays the same. [COMPATIBILITY.md](./COMPATIBILITY.md) lists them: `ddk.*` property names, message headers, table schemas, Redis keys and error codes. A change to any of them needs a rolling-upgrade path; mention it in the pull request.

- The public Java API is compared with the last release on every build. Classes in `*.internal` packages are not public API.
- The tests under `src/test/resources/compat/<version>/` read data that a released version wrote. If your change breaks one of them, that is a breaking change to discuss, not a fixture to regenerate.
- Architecture rules in `ddk-archguard-starter` never get stricter; add a new rule instead.

## Documentation

Module READMEs live next to the code. The user guide is on [domaindrivenkit.com/ddk](https://domaindrivenkit.com/ddk/), whose sources are in a separate repository; a maintainer updates it when your change is released.
