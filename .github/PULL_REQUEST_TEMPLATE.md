<!-- Title: a Conventional Commit in English, for example "fix(cache): keep the local copy when the redis eviction fails". -->

## What and why

<!-- The change, its background, and the decisions behind it. Link the issue it closes. -->

## Checklist

- [ ] `mvn verify` passes locally
- [ ] Tests cover the new behavior and its failure paths
- [ ] User-visible changes are in the `[Unreleased]` section of `CHANGELOG.md`
- [ ] The module README is updated if behavior, configuration or public API changed
- [ ] If this changes a `ddk.*` property, a message header, a table, a Redis key or an error code: the rolling-upgrade path is described above (see `COMPATIBILITY.md`)
