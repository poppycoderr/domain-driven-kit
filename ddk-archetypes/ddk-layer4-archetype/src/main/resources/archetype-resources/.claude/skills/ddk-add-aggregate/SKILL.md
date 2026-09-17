---
name: ddk-add-aggregate
description: Add a new aggregate to this DDK four-layer service - typed identifier, aggregate root, repository port and implementation, PO, MyBatis-Plus mapper, both converters and the table. Use when the user asks for a new domain concept that has its own identity and lifecycle, such as an order, a product or an account.
---
#set( $h2 = '##' )

# 新增聚合

以聚合 `Order` 为例，根包 `${package}`。把 `Order` 换成实际名称。

$h2 1. 先确认边界

- 它有独立的身份和生命周期吗？没有的话应该是值对象，不是聚合。
- 列出不变量（任何时候都必须成立的规则）和会发生的状态变化，它们决定聚合的方法。
- 其他聚合只通过标识引用它，不持有对象引用。

$h2 2. 领域层（不得引入 Spring、MyBatis、Jackson）

| 文件 | 要点 |
|---|---|
| `domain/model/OrderId.java` | `final class OrderId extends Identifier<Long>`，私有构造器校验取值，提供 `static of(Long)` |
| `domain/model/Order.java` | `extends AggregateRoot<OrderId>`；私有无参构造器；`static create(...)` 校验不变量并 `registerEvent(...)`；`static restore(..., Long version)` 调用 `assignId` 与 `assignVersion`，不登记事件；状态变更方法守卫不变量；只暴露读方法，不写 setter |
| `domain/model/*.java` | 值对象用 record 实现 `ValueObject`，在紧凑构造器里校验 |
| `domain/error/OrderError.java` | `enum OrderError implements ErrorCode`，文案用 `{0}` 占位 |
| `domain/acl/OrderRepository.java` | `interface OrderRepository extends GenericRepository<Order, Long>`，只声明领域真正需要的查询 |

违反不变量时抛 `new BusinessException(OrderError.XXX, 参数)`。

$h2 3. 基础设施层

| 文件 | 要点 |
|---|---|
| `infrastructure/orm/po/OrderPO.java` | 表的镜像，Lombok `@Data`；主键 `@TableId(type = IdType.ASSIGN_ID)`；版本列 `@Version private Long version` |
| `infrastructure/orm/mapper/OrderMapper.java` | `interface OrderMapper extends BaseMapper<OrderPO>`，加 `@Mapper` |
| `infrastructure/converter/OrderPoConverter.java` | `@Component` + `@EnhancedMapper(source = Order.class, target = OrderPO.class)`，实现 `ObjectMapper<Order, OrderPO>`，手写字段映射，带上 `version` |
| `infrastructure/converter/OrderEntityConverter.java` | 反方向，经由 `Order.restore(...)` 重建 |
| `infrastructure/acl/impl/OrderRepositoryImpl.java` | `@Repository`，`extends GenericRepositoryImpl<Order, Long, OrderPO, OrderMapper> implements OrderRepository`；自定义查询用 `Wrappers.lambdaQuery(OrderPO.class)`，结果经 `toEntity()::map` 转换 |

两个方向的转换器缺一个，启动或首次使用时就会抛 `MissingMapperException`。

$h2 4. 表结构

在 `src/main/resources/schema.sql`（或项目使用的迁移脚本）中建表：主键 `BIGINT`、`version BIGINT NOT NULL DEFAULT 0`、`create_time` 与 `update_time`。

$h2 5. 测试与验证

- 为聚合写纯单元测试：工厂方法登记了事件、非法输入抛出对应错误码、状态变更守住不变量。不需要启动 Spring。
- 运行 `mvn verify`，`ArchitectureTest` 必须通过。
