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

事件由 `GenericRepositoryImpl` 在保存后交给 `DomainEventPublisher`，事务提交后才会真正送达，无需在应用服务里手动发布。

$h2 3. 订阅

`application/handler/OrderEventHandler.java`：

- `@Component`，方法上加 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`。
- 原事务回滚时不会收到事件；执行时原事务已结束，需要写库的话在处理方法上加 `@Transactional(propagation = Propagation.REQUIRES_NEW)`。
- 处理失败不会回滚已提交的业务数据，需要可靠投递时记录失败并重试，不要假设一定成功。

$h2 4. 测试与验证

- 聚合单元测试断言 `domainEvents()` 中包含该事件，重复操作不会重复登记。
- 运行 `mvn verify`，`ArchitectureTest` 必须通过。
