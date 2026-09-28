# DDK Event Starter

Publishes the domain events that aggregate roots collect, and hands the ones marked `@IntegrationEvent` to Spring Modulith for reliable delivery outside the process.

```text
aggregate.registerEvent(e) ──► repository save ──► DomainEventPublisher ──► Spring ApplicationEventPublisher
                                                                              │
             in-process   @TransactionalEventListener(AFTER_COMMIT) ◄──────────┤
             out-of-process (@IntegrationEvent + Spring Modulith)  ◄──────────┘
                            recorded in event_publication in the same transaction,
                            sent to Kafka / AMQP / JMS / Spring Messaging after commit
```

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-event-starter</artifactId>
</dependency>
```

`GenericRepositoryImpl` publishes an aggregate's events after a successful write. Subscribers use `@TransactionalEventListener(phase = AFTER_COMMIT)`, so a rolled-back transaction notifies no one.

## Integration events

Mark a domain event that other services need, and name its destination:

```java
@IntegrationEvent(value = "user-events", key = "userId", id = "eventId")
public record UserRegisteredEvent(UUID eventId, UserId userId, String username, Instant occurredOn) implements DomainEvent {
}
```

The annotation lives in `com.ddk.core.domain` and is plain JDK, so the domain layer still depends on no framework. To deliver the events, add Spring Modulith's JDBC registry and a broker module:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-events-kafka</artifactId>   <!-- or -amqp, -jms, -messaging -->
</dependency>
```

The Modulith versions are managed by the DDK BOM.

| Step | Behavior |
|---|---|
| Publish | The event is written to `event_publication` in the business transaction. It shares the `DataSource` and transaction with MyBatis-Plus, so a rollback leaves no record |
| Deliver | After commit, Modulith sends the event to `value()`: a Kafka topic, an AMQP exchange, a JMS destination or a `MessageChannel` bean |
| Key | `key()` names a no-argument accessor; a typed identifier contributes its raw value. Messages with the same key keep their order in Kafka |
| Event ID | `id()` names an accessor whose value goes into the `ddk-event-id` header. It must be part of the event, not generated at delivery time, so that a resubmitted event keeps the same ID |
| Failure | A failed delivery stays in `event_publication` and can be resubmitted through Modulith's `IncompleteEventPublications` / `FailedEventPublications` |
| Payload | Typed identifiers are written as raw JSON values, e.g. `"userId":42`, by the `IdentifierJacksonModule` this starter registers |

If the application declares its own `EventExternalizationConfiguration`, DDK's selection backs off.

## Event contracts

Every delivered integration event carries contract headers:

| Header | Source | Purpose |
|---|---|---|
| `ddk-event-type` | `type()`, defaults to the simple class name | Consumers dispatch on this stable name, not on the Java class, so moving or renaming the class does not break them. Put the old name in `type()` when renaming |
| `ddk-event-version` | `version()`, defaults to `1` | Which shape of the payload this is |
| `ddk-event-id` | `id()` accessor | Deduplication, see below |

Evolution rules:

```text
same version     add optional fields only; consumers must ignore fields they don't know
breaking change  remove / rename a field, change its meaning or type
                 → new event class with version = N + 1 and the same type()
                 → publish both versions until every consumer has moved
                 → then remove the old one
```

At startup the starter scans the application's packages for `@IntegrationEvent` classes and fails fast when a declaration is wrong:

- `key()` or `id()` does not name a no-argument accessor
- `version()` is below 1 or the target is blank
- two classes declare the same type and version

Without this check a typo would surface only after commit, when delivery fails and the publication is left `FAILED`.

## Idempotent consumers

Delivery is at least once, so a consumer can see the same message twice. `IdempotentConsumer` runs a handler only once per consumer and message ID:

```java
@KafkaListener(topics = "user-events")
void on(UserRegisteredEvent event, @Header("ddk-event-id") String eventId) {
    idempotentConsumer.handle("welcome-mail", eventId, () -> mailService.sendWelcome(event.userId()));
}
```

```text
handle(consumer, messageId, handler)            joins the caller's transaction, or starts one
  savepoint: INSERT (consumer, message_id)       primary key conflict → roll back to savepoint, return false
  handler.run()                                  throws → the whole transaction, including the insert, rolls back
  return true
```

- The insert and the handler's own writes share one transaction. A failed handler leaves the message unprocessed, so a redelivery runs it again.
- The savepoint matters on PostgreSQL, which aborts the whole transaction after a failed statement. A duplicate rolls back only to the savepoint, and the caller's other writes still commit. A Testcontainers test verifies this.
- `purgeOlderThan(Duration)` removes old records. Keep them longer than the broker's longest redelivery window.

Enable it with `ddk.event.inbox.enabled=true`. It needs a `JdbcTemplate` and a transaction manager, and creates `ddk_processed_message` with `CREATE TABLE IF NOT EXISTS`, which works on MySQL, PostgreSQL and H2. On other databases, or when a migration tool owns the schema, create the table yourself and set `initialize-schema=false`:

```sql
CREATE TABLE ddk_processed_message (
    consumer     VARCHAR(200) NOT NULL,
    message_id   VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (consumer, message_id)
);
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.event.enabled` | `true` | Register the Spring-backed `DomainEventPublisher` |
| `ddk.event.inbox.enabled` | `false` | Register `IdempotentConsumer` |
| `ddk.event.inbox.table` | `ddk_processed_message` | Processed message table |
| `ddk.event.inbox.initialize-schema` | `true` | Create the table on startup |
| `spring.modulith.events.*` | Spring Modulith | Registry schema, republishing on restart, completion mode, staleness |

## Not covered yet

- RocketMQ: Spring Modulith has no RocketMQ module
