# 兼容性

[English](./COMPATIBILITY.md)

DDK 承诺在版本之间保持哪些东西可用，哪些不承诺。1.0 之前承诺更弱（见[1.0 之前](#10-之前)）；这一页同时也是 1.0 要冻结的清单。

## 版本号

从 1.0 起，本页标为**稳定**的内容遵循[语义化版本](https://semver.org/lang/zh-CN/)：

| 版本 | 可能包含 |
|---|---|
| 补丁版本（`1.2.3` → `1.2.4`） | 只有修复。没有新 API，不改文档写明的默认值 |
| 次版本（`1.2` → `1.3`） | 新增：类型、方法、配置项、规则、starter；弃用；让 DDK 的行为符合文档原本描述的修复 |
| 主版本（`1.x` → `2.0`） | CHANGELOG 里 **Breaking** 下列出的任何变化，每一项都在升级指南里给出迁移步骤 |

要删除的东西，先在某个次版本里标为 `@Deprecated(since = "...", forRemoval = true)`（不是 Java 的，在文档里标为弃用），最早在下一个主版本删除。

## 稳定的部分

### Java API

已发布模块中 `*.internal` 包之外的 public 和 protected 成员：

| 模块 | 稳定的类型 |
|---|---|
| `ddk-core` | 领域模型（`AggregateRoot`、`Entity`、`Identifier`、`ValueObject`、`DomainEvent`、`AbstractDomainEvent`、`DomainEventPublisher`、`IntegrationEvent`、`Specification`）、`GenericRepository` 与 `ConcurrentUpdateException`、分页（`PageQuery`、`PageResponse`、`Sort`）、异常与 `ErrorCode` / `CommonError`、`ApiResponse`、`OperatorContext` / `Operator` / `DataScope`、映射注册（`MapperProvider`、`ObjectMapper`、`EnhancedMapper`、`MapperConfiguration`、`EnumMapper`、`IEnum`）、`IdentifierJacksonModule` |
| `ddk-mybatis` | `GenericRepositoryImpl`、`MybatisPlusPageAdapter`、`@Query`、`Operator`、`QueryParser` |
| `ddk-test` | `DdkAssertions`、`AggregateAssert`、`BusinessRuleAssert`、`DdkContainers` 及其容器类型 |
| 各 starter | 应用注入、实现、标注或捕获的类型：`@AggregateLock`、`AggregateLocks`、`@Idempotent`、`@RateLimit`；`IntegrationEventConsumer`、`ReceivedEvent`、`EventContract`、`IntegrationEventDispatcher`、`IdempotentConsumer`；`Projection`、`Projections`、`FailedRefresh`；`DataScopeResolver`；`OperatorResolver`、`BaseExceptionHandler`；`CacheSettings`、`DdkCacheManager`、`TwoLevelCache`、`CacheInvalidationPublisher`、`CacheMetrics`；`RedisJsonMapper`；`CommonArchRules`、`ArchGuard`、`ArchGuardReport`、`Violation` |

新增带默认实现的方法、带默认值的注解元素，都是兼容的；改变参数列表不兼容，新参数以新增重载的方式加入。新增重载在二进制上兼容，但可能让已有的、传 `null` 的调用在编译时出现歧义，所以每个新重载都要为此审查。

CI 在每次改动时检查这一点：japicmp 把每个模块与上一个发布版本比较，二进制或源码不兼容的变化会让构建失败，除非它被列为有意的破坏（见 `AGENTS.md`）。

### 配置

- **`ddk.*` 下的配置项名称和默认值**，以各 starter 的 README 和文档站为准。次版本里改名时，旧名称必须继续可用并标为弃用。
- **自动配置类的类名**（`Ddk*AutoConfiguration`），因为应用会在 `spring.autoconfigure.exclude` 里写它们。它们的 `@Bean` 方法是包级可见的，不属于 API；Bean 的类型和 `@ConditionalOnMissingBean` 的约定（应用自己的 Bean 替换 DDK 的）属于 API。
- **默认关闭的功能保持关闭。** 次版本不会打开一个会改变行为的功能。

### 数据与传输格式

比一次部署活得更久、或者在不同 DDK 版本的实例之间传递的数据。滚动发布期间新旧实例会读对方写的消息、表记录和 Redis 数据，所以名称和取值都属于契约。

| 契约 | 固定的内容 | 所在 |
|---|---|---|
| 集成事件消息 | 消息头名称 `ddk-event-id`、`ddk-event-type`、`ddk-event-version`、`ddk-event-tenant`。取值：`@IntegrationEvent(id)` 指定的事件标识；类型（`type`，或事件类的简单类名）；十进制字符串形式的版本；租户。消息体是事件的 JSON，带类型的标识写成原始值。主题和标签来自 `@IntegrationEvent(value)` 的 `主题:标签`，消息 key 来自 `key` | Event starter |
| 去重消费方的身份 | 已处理的消息按 `消费组:consumerId` 加事件标识记录；`consumerId()` 默认是消费方类的简单类名。改消费方的类名或 `consumerId()`，重投的事件会再处理一次 | Event starter |
| 表 `ddk_projection_pending` 和 `ddk_processed_message`（表名可配置） | 列、主键和到期索引；上一个版本写下的标记，下一个版本照常处理 | Projection 和 Event starter |
| Outbox（`event_publication`） | 表结构和状态值属于 Spring Modulith，DDK 在 Modulith 默认的 JDBC 结构上验证。DDK 负责写进去的内容：`event_type` 是事件类的全名，`serialized_event` 是事件的 JSON。移动或改名事件类会让它还没完成的发布无法处理，所以先等它们完成 | Event starter、Spring Modulith |
| Redis key | 锁 `ddk:lock:<类型>:<标识>`、防重复提交 `ddk:idempotent:<scope>:<key>`、限流 `ddk:rate:<scope>:<额度>/<周期>ms:<key>`、缓存 `ddk:cache:<缓存名>::<key>`，前缀都可配置。定时任务锁的 key 属于 ShedLock，DDK 设置它的前缀 `ddk:job-lock:<应用名>:` | Concurrency、Cache、Job starter |
| Redis 值 | Redis starter 带类型信息的 JSON（`@class`，集合和数字用带类型标注的数组）。DDK 保持这种编码；里面装的类以及它们怎么演进，由应用负责 | Redis 和 Cache starter |
| 缓存失效广播 | 频道 `ddk:cache:invalidation`（前缀可配置）和消息 `{"origin", "cacheName", "key"}`，`key` 为 `null` 表示清空整个缓存 | Cache starter |
| HTTP | `ApiResponse` 的 JSON（`code`、`message`、`data`、`timestamp`）、`CommonError` 的错误码及其 HTTP 状态、返回 traceId 的响应头（默认 `X-Trace-Id`） | Core、Web、Tracer starter |
| ArchGuard 报告的格式 | 机器可读报告的字段 | ArchGuard starter |

改动其中任何一项，都要给出滚动发布的过渡办法：新旧实例必须能同时运行，否则升级指南要写明不能，以及该怎么做。表结构的变化由 starter 自动处理，或者附带迁移 SQL。

这些契约用已发布版本实际产生的数据测试：[`compat/`](./compat/README.md) 里有一个生成器，它从 Maven 中央仓库拉取一个已发布的 DDK 版本来运行；各模块读取那个版本的 fixture，并检查自己写出的数据仍然一样。japicmp 不检查 JSON、SQL、Redis 值和消息内容。

### 架构规则

`CommonArchRules` 里已有的规则不会在次版本里变严，因为那会让原来能通过的构建失败。更严格的检查以新规则的形式加入，由项目自己选择启用，最早在下一个主版本替换旧规则。如果规则报出了文档允许的写法，可以放宽。

## 不稳定的部分

- **`*.internal` 包。** 只由自动配置创建的实现类。生成的项目用 `DDK_INTERNALS_MUST_NOT_BE_USED` 检查这一点。
- **实验性模块：** `ddk-db-starter` 和 `ddk-seata-starter`。它们只经过装配层面的测试（见[支持状态](./ROADMAP.zh-CN.md#支持状态)），任何版本都可能改变。对真实系统验证之后才会移出这个列表。
- **示例**（`ddk-examples/*`）：参考代码，不是库。
- **日志内容、文档之外的指标标签、异常消息。** 代码要依据异常类型和错误码，而不是消息文本。
- **生成项目的模板。** archetype 的新版本会生成不同的骨架；之前生成的项目不受影响。

## 1.0 之前

0.x 阶段，次版本可能破坏本页的任何内容。每一项破坏都列在 [CHANGELOG](./CHANGELOG.md) 的 **Breaking** 下，迁移步骤见[升级指南](https://domaindrivenkit.com/ddk/upgrade)；API 检查照常运行，所以不会有无意的破坏。补丁版本从不破坏兼容。
