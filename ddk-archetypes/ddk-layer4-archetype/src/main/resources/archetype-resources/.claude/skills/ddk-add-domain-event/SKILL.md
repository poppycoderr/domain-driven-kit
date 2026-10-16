---
name: ddk-add-domain-event
description: Add a domain event and its subscriber to this DDK four-layer service. Use when something that happened in an aggregate should trigger follow-up work, such as sending a notification after an order is paid, without the aggregate knowing about that work.
---
#set( $h2 = '##' )

# 新增领域事件

以「订单已支付」为例，根包 `${package}`。

$h2 1. 定义事件

`domain/event/OrderPaidEvent.java`：

- record 实现 `DomainEvent`，用过去式命名，描述已经发生的事实。
- 只携带订阅方需要的数据，引用其他聚合时只放标识（如 `OrderId`），不放聚合对象。
- 包含 `Instant occurredOn`，并提供一个不带它的便捷构造器，内部用 `Instant.now()`。

$h2 2. 在聚合里登记

在产生这个事实的聚合方法里调用 `registerEvent(new OrderPaidEvent(id(), ...))`。只在状态真正发生变化时登记：重复调用的幂等方法不要重复登记。从数据库重建（`restore`）时不登记。

事件由 `GenericRepositoryImpl` 在保存时交给 `DomainEventPublisher`，无需在应用服务里手动发布。发布发生在保存的那一刻、还在业务事务里；订阅方什么时候收到，取决于下一步选的监听方式。

$h2 3. 订阅

`application/handler/OrderEventHandler.java`，`@Component`。先想清楚这件后续工作**必须完成**还是**可以丢**，再选监听方式，不要所有监听器都用同一种：

| 目的 | 监听方式 | 保证 |
|---|---|---|
| 把领域事件翻译成对外的集成消息（`@IntegrationEvent`） | `@EventListener`，在原事务里同步执行 | 消息和业务数据一起提交、一起回滚；提交后由事件 starter 投递 |
| 标记读模型需要刷新（`projections.markDirty`） | `@EventListener`，在原事务里同步执行 | 不会出现「数据改了、刷新标记没写」 |
| 可以丢的通知，例如刷新进程内的缓存、打日志 | `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` | 只在提交后执行；进程这时崩溃就丢了，失败也不会回滚业务数据 |
| 必须完成、又不该拖慢原事务的工作，例如发通知邮件 | 发一条集成事件（第一行），由消费方处理 | 持久化、可重投，重启不丢；消费方要能重复执行 |

注意：

- `@EventListener` 里抛出异常会让原事务回滚，只放必须和业务一起成败的事。
- `AFTER_COMMIT` 的处理方法执行时原事务已经结束，要写库就在方法上加 `@Transactional(propagation = Propagation.REQUIRES_NEW)`。
- 不要用 `AFTER_COMMIT` 做「必须完成」的事：它在内存里，提交之后、执行之前进程崩溃，这件事就悄悄没做。

$h2 4. 测试与验证

- 聚合单元测试断言 `domainEvents()` 中包含该事件，重复操作不会重复登记。
- 运行 `mvn verify`，`ArchitectureTest` 必须通过。
