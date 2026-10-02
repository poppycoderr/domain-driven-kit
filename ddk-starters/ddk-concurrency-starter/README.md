# DDK Concurrency Starter

Concurrency control keyed by domain concepts, on Redisson: one operation at a time per aggregate instance, one execution per client request, and a call budget per user or tenant.

```text
@RateLimit         take one permit for the key             none left → RateLimitedException (429)
  @Idempotent        register the request (SET NX + TTL)   duplicate → DuplicateRequestException (409)
    @AggregateLock     acquire ddk:lock:<type>:<id>        not acquired in time → AggregateBusyException (409)
      @Transactional     load → change → save → commit
    release the lock                                       after the commit, so the next operation loads committed state
on failure: release the lock, remove the request registration
```

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-concurrency-starter</artifactId>
</dependency>
```

```java
@Transactional
@AggregateLock(type = "order", id = "#command.orderId()")
@Idempotent(key = "#command.requestId()")
public OrderResponse pay(PayOrderCommand command) {
    Order order = orders.findById(command.orderId()).orElseThrow(...);
    order.pay();
    orders.update(order);
    return OrderResponse.from(order);
}
```

The starter uses the application's `RedissonClient` when there is one. Otherwise it creates a single-server client from Spring Boot's Redis connection details (`spring.data.redis.*`). For Sentinel, Cluster or custom TLS, declare your own `RedissonClient`. It depends on Redisson's core client only, not on Redisson's Spring Boot starter, so your existing Spring Data Redis connection factory is left alone.

If no `RedissonClient` is available, calling an annotated method fails with an `IllegalStateException` instead of running without the lock.

## Aggregate locks

| Aspect | Behavior |
|---|---|
| Lock name | `<key-prefix>lock:<type>:<id>`. `id` is a SpEL expression over the method parameters; a typed identifier contributes its raw value |
| Waiting | `waitTime` on the annotation, or `ddk.concurrency.lock.wait-time`. When the lock is not acquired in time, `AggregateBusyException` (`AGGREGATE_BUSY`) is thrown and the web starter answers 409 |
| Holding | Without a lease time, Redisson's watchdog renews the lock until the method returns. With `leaseTime`, the lock expires on its own, and a warning is logged if the method outlives it |
| Transactions | The lock wraps the transaction of the annotated method: acquired before it begins, released after it commits or rolls back. A test checks that the lock is still held in `afterCommit` |
| Reentrancy | The same thread can take the same lock again |

Optimistic locking stays in place. It guarantees that two writers never overwrite each other; the aggregate lock makes them queue, so the second one does not do its work only to have it rejected at commit.

The lock only wraps the transaction when the annotated method starts it. If the caller is already inside a transaction, the lock is released before that outer transaction commits. Put the annotation on the outermost application service method.

For code that is not a Spring bean method, inject `AggregateLocks`:

```java
aggregateLocks.execute("order", orderId, () -> transaction.execute(status -> ...));
```

## Duplicate-submit protection

`@Idempotent` registers `<key-prefix>idempotent:<scope>:<key>` with `SET NX` and a TTL before the method runs.

- The key should come from the client and stay the same across retries, such as a request ID generated when the form is opened.
- A second call with the same key inside the TTL throws `DuplicateRequestException` (`DUPLICATE_REQUEST`, 409). The first result is not replayed.
- If the method throws, the registration is removed and the client can retry with the same key.
- `scope` defaults to `ClassName.methodName`, so the same key in two use cases does not collide.
- A duplicate is rejected before the lock is requested, so it does not spend the lock's wait time.

This protects against double clicks and client retries. For consumers of at-least-once messages, use `IdempotentConsumer` from the event starter, which registers the message in the same database transaction as the handler's writes.

## Rate limits

```java
@RateLimit(limit = 20, period = "1m", key = "#query.tenantId()")
public PageResponse<OrderResponse> search(OrderQuery query) { ... }
```

- Each key has its own budget of `limit` calls per `period`; a typed identifier contributes its raw value. Without `key`, the whole use case shares one budget.
- The count lives in Redis (Redisson's `RRateLimiter`), so all instances share it.
- Over the limit, `RateLimitedException` (`RATE_LIMITED`) is thrown and the web starter answers 429.
- It runs before `@Idempotent` and `@AggregateLock`: a limited call does not register its request key and does not wait for a lock.
- The limit and period are part of the Redis key, `<key-prefix>rate:<scope>:<limit>/<period>:<key>`. Changing them in code takes effect on deployment, and the old keys expire after two idle periods.

This is a business limit, such as exports per tenant or SMS codes per phone number. It runs inside the application after the request has been parsed, so it does not replace gateway rate limiting against traffic floods.

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.concurrency.enabled` | `true` | Turn the annotations and the client off, for local troubleshooting only |
| `ddk.concurrency.key-prefix` | `ddk:` | Prefix of every Redis key; set it per application on a shared Redis |
| `ddk.concurrency.lock.wait-time` | `3s` | Default time to wait for a lock |
| `ddk.concurrency.lock.lease-time` | | Default time to hold a lock; unset means the watchdog renews it |
| `ddk.concurrency.idempotent.ttl` | `10m` | Default lifetime of a request registration |

## Not covered yet

- The reference example does not use this starter yet, because it runs without Redis
- A lock is only as reliable as a single Redis: after a failover, a lock that was not yet replicated can be granted twice. Keep the version check on the aggregate
