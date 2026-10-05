# 参考应用：商城 (ddk-mall)

用一个完整的业务场景把 DDK 的各个 starter 串起来：下单 → 预占库存 → 支付 → 完成，超时未支付则取消并释放库存。订单、库存、支付三个限界上下文部署成一个应用（模块化单体），上下文之间只通过集成事件协作。

这个应用分步建设，目前完成了前 2 步。

| 步骤 | 内容 | 状态 |
|---|---|---|
| 1 | 骨架、订单上下文（下单、查询、取消）、Flyway、MySQL | 已完成 |
| 2 | 库存上下文：预占、释放、扣减，按 SKU 加锁 | 已完成 |
| 3 | 订单与库存之间的事件走 RocketMQ，幂等消费 | 未开始 |
| 4 | 支付上下文（模拟渠道）、超时关单 | 未开始 |
| 5 | 缓存、接口文档、MCP 工具 | 未开始 |
| 6 | 可观测性、整体文档 | 未开始 |

## 运行

默认使用内存 H2，不依赖任何外部组件：

```bash
mvn -pl ddk-examples/ddk-mall -am install -DskipTests
mvn -pl ddk-examples/ddk-mall spring-boot:run
```

连接真实的 MySQL 和 Redis：

```bash
docker compose -f ddk-examples/ddk-mall/docker-compose.yml up -d
mvn -pl ddk-examples/ddk-mall spring-boot:run -Dspring-boot.run.profiles=compose
```

表结构由 Flyway 在启动时创建，并带三件商品和它们的库存：`SKU-KEYBOARD`（10 件）、`SKU-MOUSE`（50 件）、`SKU-MONITOR`（3 件）。

两种运行方式的差别在库存锁：`compose` profile 下锁放在 Redis 里，多个实例之间互斥；默认 profile 不连 Redis，锁退回进程内实现，只适合单实例，启动时会打一条警告日志。

## 冒烟命令

示例不做登录认证，顾客身份放在请求头 `X-Customer-Id` 里。

```bash
# 下单：只传 SKU 和数量，名称与单价取自商品目录
curl -s -X POST localhost:8080/orders -H 'X-Customer-Id: 7' -H 'Content-Type: application/json' \
  -d '{"lines":[{"skuId":"SKU-KEYBOARD","quantity":1},{"skuId":"SKU-MOUSE","quantity":2}]}'

# 查询自己的订单（把 <id> 换成上一步返回的订单号）
curl -s localhost:8080/orders/<id> -H 'X-Customer-Id: 7'

# 换一个顾客查同一个订单：ORDER_NOT_FOUND，不透露订单是否存在
curl -s localhost:8080/orders/<id> -H 'X-Customer-Id: 8'

# 我的订单
curl -s -X POST localhost:8080/orders/page -H 'X-Customer-Id: 7' -H 'Content-Type: application/json' -d '{}'

# 取消；再取消一次返回 ORDER_NOT_CANCELLABLE
curl -s -X POST localhost:8080/orders/<id>/cancel -H 'X-Customer-Id: 7' -H 'Content-Type: application/json' \
  -d '{"reason":"不想要了"}'
```

库存（第 3 步之后预占、释放、扣减由订单事件驱动，目前先手工调用）：

```bash
# 查库存：在库、已预占、可售
curl -s localhost:8080/inventory/SKU-MONITOR

# 为订单 1001 预占；任何一行库存不足，整单都不占
curl -s -X POST localhost:8080/inventory/reservations -H 'Content-Type: application/json' \
  -d '{"orderId":1001,"lines":[{"skuId":"SKU-MONITOR","quantity":2},{"skuId":"SKU-MOUSE","quantity":1}]}'

# 订单支付后扣减，或订单取消后释放；两者都可以重复调用
curl -s -X POST localhost:8080/inventory/reservations/1001/confirm
curl -s -X POST localhost:8080/inventory/reservations/1001/release

# 补货
curl -s -X POST localhost:8080/inventory/SKU-MONITOR/restock -H 'Content-Type: application/json' -d '{"quantity":5}'
```

## 结构

```text
com.example.mall
├── MallApplication
├── platform                 各上下文共用的技术代码：从请求头解析顾客身份
├── order                    订单上下文，内部是四层
│   ├── adapter/controller
│   ├── application/{command,query,response,service,handler}
│   ├── domain/{model,event,acl,error}
│   └── infrastructure/{acl/impl,converter,id,orm}
└── inventory                库存上下文，同样是四层；订单在这里只是一个编号
    ├── adapter/controller
    ├── application/{command,response,service}
    ├── domain/{model,event,acl,error}
    └── infrastructure/{acl/impl,converter,id,lock,orm}
```

`ArchitectureTest` 除了 DDK 的分层规则，还检查上下文之间互不引用：`order`、`inventory`、`payment` 三个包不能依赖彼此。

## 用到了什么

| 做法 | 位置 |
|---|---|
| 聚合带子表：订单行随订单整体保存和加载，应用层看不到订单行表 | `OrderRepositoryImpl` 覆盖 `afterInsert`、`afterLoad`、`beforeRemove` |
| 值对象自我校验：金额、订单行 | `Money`、`OrderLine` |
| 端口与适配：订单只知道「下单那一刻的商品名称和单价」 | `ProductCatalog` 与 `ProductCatalogImpl` |
| 操作者上下文与审计字段：`create_by` 自动写入顾客 ID | `CustomerIdentity`、`OrderPO` |
| 乐观锁：过期的订单不能覆盖新的 | `OrderPO.version`，`OrderMysqlIntegrationTest` |
| 领域断言 | `OrderTest` |
| 在真实 MySQL 上验证迁移脚本与仓储 | `OrderMysqlIntegrationTest`，用 `DdkContainers.mysql()` |
| 预占与扣减分开：预占的货还在库里，支付后才出库 | `Stock` |
| 可重复执行：每个订单每个 SKU 一条预占记录，释放和扣减据此判断是否已经做过 | `StockReservation`，`(order_id, sku_id)` 唯一索引 |
| 一次锁多个聚合，按固定顺序加锁避免死锁 | `StockLockImpl` 调用 `AggregateLocks.executeAll` |
| 锁包住事务：先拿锁，再在锁里开启并提交事务 | `InventoryService` 用 `TransactionTemplate` |
| 不超卖：20 个订单同时抢 3 件库存，恰好 3 个成功 | `StockConcurrencyIntegrationTest`，真实的 MySQL 和 Redis |
