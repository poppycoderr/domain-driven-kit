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
| `redis.json` | a value through the Redis starter's serializer, the cache invalidation messages, an aggregate lock key | `RedisValueCompatibilityTest`, `CacheMessageCompatibilityTest`, `LockKeyCompatibilityTest` (as `lock-key.txt`) |

The tests live in the module that owns each format, under `src/test/resources/compat/<version>/`. Each test checks both directions: the current code reads what the released version wrote, and writes the same thing, so an old instance can read it too. A change that has to break one of them is a deliberate break: it goes under **Breaking** in the changelog with a rolling-upgrade path, and its test is updated to say so.

After every release, generate the fixtures of that release and add them next to the earlier ones.
