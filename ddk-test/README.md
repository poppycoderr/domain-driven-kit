# DDK Test

Test support for DDK projects: assertions on aggregates and business rules, and Testcontainers presets for the middleware DDK integrates with. Add it with test scope.

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-test</artifactId>
    <scope>test</scope>
</dependency>
```

## Domain assertions

`DdkAssertions` sits next to AssertJ's `Assertions`. Aggregate tests need no Spring context.

```java
Order order = Order.place(customerId, lines);
assertThat(order).hasRaisedExactly(OrderPlacedEvent.class);

order.clearEvents();
order.pay();
assertThat(order).hasRaised(OrderPaidEvent.class, event -> assertThat(event.orderId()).isEqualTo(order.id()));

order.clearEvents();
assertThatRejected(order::cancel)
        .withCode(OrderError.ORDER_NOT_CANCELLABLE)
        .withoutRaisingEventsOn(order);
```

| Assertion | Checks |
|---|---|
| `hasRaised(type)` | At least one event of the type was registered |
| `hasRaised(type, requirements)` | Exactly one event of the type, and it satisfies the given assertions |
| `hasRaisedExactly(types...)` | The registered events have exactly these types, in this order |
| `hasNotRaised(type)` / `hasRaisedNoEvents()` | No such event, or none at all |
| `assertThatRejected(action)` | The action threw a `BusinessException` |
| `.withCode(errorCode)` / `.withArgs(args...)` | The rejection carries this error code and these message arguments |
| `.withoutRaisingEventsOn(aggregate)` | The rejected operation left no events on the aggregate |

The assertions read the events still held by the aggregate and do not drain them. Call `clearEvents()` before the step you want to look at. Rejections are matched by error code, not by message text: the text may change, the code is the contract with callers. A failed assertion lists the events the aggregate actually raised.

## Container presets

```java
@Testcontainers(disabledWithoutDocker = true)
class OrderEventsTest {

    @Container
    static final RocketMqContainer ROCKETMQ = DdkContainers.rocketmq();

    @DynamicPropertySource
    static void rocketmq(DynamicPropertyRegistry registry) {
        registry.add("ddk.event.rocketmq.name-server", ROCKETMQ::getNameServer);
    }

    @BeforeAll
    static void topic() {
        ROCKETMQ.createTopic("order-events", 4);
    }
}
```

| Preset | Image | Notes |
|---|---|---|
| `DdkContainers.redis()` | `redis:7-alpine` | `getHost()` and `getPort()` |
| `DdkContainers.mysql()` | `mysql:8.4` | `utf8mb4`; needs `org.testcontainers:testcontainers-mysql` and the MySQL driver on the test classpath |
| `DdkContainers.rocketmq()` | `apache/rocketmq:5.5.0` | NameServer and one broker in one container; `getNameServer()`, `createTopic(topic, queues)` |

RocketMQ is the one that is hard to get right by hand. Clients ask the NameServer for the broker's address and then connect to the broker directly, so the address the broker registers must be reachable from the host. The preset picks a free host port, makes the broker listen on the same port inside the container and maps it one to one. Topic auto-creation is off; `createTopic` creates the topic on the broker and returns once its route is visible on the NameServer, so the first send does not fail with "No route info".

DDK's own integration tests use these presets.
