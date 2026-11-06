# 库存示例：Redis 上的 starter (ddk-example-inventory)

一个小的四层应用，把依赖 Redis 的 starter 放在一起：两级缓存、聚合锁、防重复提交、限流，再加上提交后刷新的投影和经 Spring Modulith 发布登记运行的事件监听器。CI 把它编译成 GraalVM 原生镜像，用 `scripts/native-smoke-inventory.sh` 检查同一组请求；JVM 上的同样检查是 `InventoryApiTest`。

## 运行

需要一个 Redis，默认 `localhost:6379`，用 `REDIS_HOST`、`REDIS_PORT` 改：

```bash
docker run -d --name inventory-redis -p 6379:6379 redis:7.4
mvn -pl ddk-examples/ddk-example-inventory -am install -DskipTests
mvn -pl ddk-examples/ddk-example-inventory spring-boot:run
```

种子数据里有两个商品：1 号 `apple` 库存 10，2 号 `pear` 库存 8。库存低于 5 的商品进入「库存偏低」读模型。

## 冒烟命令

```bash
# 查询：第一次读数据库并写入两级缓存，之后从缓存读
curl -s localhost:8080/skus/1

# 预留：按商品加锁，按请求号防重复；同一个 requestId 第二次返回 409 DUPLICATE_REQUEST
curl -s -X POST localhost:8080/skus/1/reservations -H 'Content-Type: application/json' -d '{"requestId":"r-1","quantity":6}'

# 可用量检查：每个商品每分钟 3 次，第 4 次返回 429 RATE_LIMITED
curl -s localhost:8080/skus/2/availability

# 投影：提交之后刷新的「库存偏低」读模型
curl -s localhost:8080/low-stock

# 发布登记：预留提交后由监听器记下的流水
curl -s localhost:8080/skus/1/reservations
```

## 原生镜像

```bash
mvn -pl ddk-examples/ddk-example-inventory -am install -DskipTests
mvn -Pnative -DskipTests -pl ddk-examples/ddk-example-inventory native:compile
scripts/native-smoke-inventory.sh ddk-examples/ddk-example-inventory/target/ddk-example-inventory
```

缓存里的 `SkuResponse` 由 Redis 的 JSON 序列化读写，所以 `InventoryService` 用 `@RegisterReflectionForBinding` 登记了它；DDK 自己需要的反射由各 starter 登记。
