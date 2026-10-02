# 参考应用：商城 (ddk-mall)

用一个完整的业务场景把 DDK 的各个 starter 串起来：下单 → 预占库存 → 支付 → 完成，超时未支付则取消并释放库存。订单、库存、支付三个限界上下文部署成一个应用（模块化单体），上下文之间只通过集成事件协作。

这个应用分步建设，目前完成的是第 1 步。

| 步骤 | 内容 | 状态 |
|---|---|---|
| 1 | 骨架、订单上下文（下单、查询、取消）、Flyway、MySQL | 已完成 |
| 2 | 库存上下文：预占、释放、扣减 | 未开始 |
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

连接真实的 MySQL：

```bash
docker compose -f ddk-examples/ddk-mall/docker-compose.yml up -d
mvn -pl ddk-examples/ddk-mall spring-boot:run -Dspring-boot.run.profiles=mysql
```

表结构由 Flyway 在启动时创建，并带三件商品：`SKU-KEYBOARD`、`SKU-MOUSE`、`SKU-MONITOR`。

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

## 结构

```text
com.example.mall
├── MallApplication
├── platform                 各上下文共用的技术代码：从请求头解析顾客身份
└── order                    订单上下文，内部是四层
    ├── adapter/controller
    ├── application/{command,query,response,service,handler}
    ├── domain/{model,event,acl,error}
    └── infrastructure/{acl/impl,converter,id,orm}
```

`ArchitectureTest` 除了 DDK 的分层规则，还检查上下文之间互不引用：`order`、`inventory`、`payment` 三个包不能依赖彼此。

## 这一步用到了什么

| 做法 | 位置 |
|---|---|
| 聚合带子表：订单行随订单整体保存和加载，应用层看不到订单行表 | `OrderRepositoryImpl` 覆盖 `afterInsert`、`afterLoad`、`beforeRemove` |
| 值对象自我校验：金额、订单行 | `Money`、`OrderLine` |
| 端口与适配：订单只知道「下单那一刻的商品名称和单价」 | `ProductCatalog` 与 `ProductCatalogImpl` |
| 操作者上下文与审计字段：`create_by` 自动写入顾客 ID | `CustomerIdentity`、`OrderPO` |
| 乐观锁：过期的订单不能覆盖新的 | `OrderPO.version`，`OrderMysqlIntegrationTest` |
| 领域断言 | `OrderTest` |
| 在真实 MySQL 上验证迁移脚本与仓储 | `OrderMysqlIntegrationTest`，用 `DdkContainers.mysql()` |
