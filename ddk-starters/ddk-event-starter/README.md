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
@IntegrationEvent(value = "user-events", key = "userId")
public record UserRegisteredEvent(UserId userId, String username, Instant occurredOn) implements DomainEvent {
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
| Failure | A failed delivery stays in `event_publication` and can be resubmitted through Modulith's `IncompleteEventPublications` / `FailedEventPublications` |
| Payload | Typed identifiers are written as raw JSON values, e.g. `"userId":42`, by the `IdentifierJacksonModule` this starter registers |

If the application declares its own `EventExternalizationConfiguration`, DDK's selection backs off.

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.event.enabled` | `true` | Register the Spring-backed `DomainEventPublisher` |
| `spring.modulith.events.*` | Spring Modulith | Registry schema, republishing on restart, completion mode, staleness |

## Not covered yet

- Idempotent consumers: delivery is at least once, so consumers must deduplicate. DDK support for this is planned for v0.3
- RocketMQ: Spring Modulith has no RocketMQ module
