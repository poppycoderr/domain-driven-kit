# Cross-version data fixtures

Data that DDK stores or sends outlives one deployment: during a rolling upgrade, old and new instances read each other's messages, tables and Redis entries. The Java API check (japicmp) does not see any of that, so these fixtures do.

`generator/` is a small application that depends on a **released** DDK version from Maven Central and writes data exactly as that version produces it. It is not part of the DDK build.

```bash
cd compat/generator
mvn -Dddk.version=0.10.0 compile exec:java    # writes target/compat/0.10.0/
```

| File | Produced by | Read by |
|---|---|---|
| `rocketmq-message.json` | the event starter's RocketMQ transport: topic, tag, keys, all message properties, body | `EventDataCompatibilityTest` |
| `outbox-publication.json` | Spring Modulith's `event_publication` row for an `@IntegrationEvent` | `EventDataCompatibilityTest` |
| `processed-message-table.json` | the processed message table: schema and the row of an idempotent consumer | `EventDataCompatibilityTest` |
| `projection-pending-table.sql` | the projection mark table: schema, due index and one mark with a tenant | `PendingTableCompatibilityTest` |
| `concurrency-keys.json` | the default scope of `@Idempotent` and `@RateLimit`, and the Redis keys they register | `ConcurrencyKeyCompatibilityTest` |
| `redis.json` | a value through the Redis starter's serializer, the cache invalidation messages, an aggregate lock key | `RedisValueCompatibilityTest`, `CacheMessageCompatibilityTest`, `LockKeyCompatibilityTest` (as `lock-key.txt`) |

The fixtures live in the module that owns each format, under `src/test/resources/compat/<version>/`, one directory per release. Each test runs once for every release directory it finds, and checks both directions: the current code reads what that release wrote, and writes the same thing, so an old instance can read it too. A missing file in a release directory fails the test that reads it.

A change that has to break one of them is a deliberate break: it goes under **Breaking** in the changelog with a rolling-upgrade path, and the test records which releases differ and how, as `ConcurrencyKeyCompatibilityTest` does for the default scope of 0.10.0. Every other release must match the current code exactly.

## After a release

The fixtures of a release are generated once, from its artifacts on Maven Central, and never regenerated:

```bash
cd compat/generator
mvn -Dddk.version=X.Y.Z compile exec:java      # writes target/compat/X.Y.Z/
cd ../..
compat/install-fixtures.sh X.Y.Z                 # copies each file into the modules that read it, refuses to overwrite
```

The build passes the API baseline (`api.baseline.version` in the root pom) to the tests, and they fail when that release has no fixtures. Moving the baseline to a new release therefore needs its fixtures in the same change.
