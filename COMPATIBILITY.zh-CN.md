# 兼容性

[English](./COMPATIBILITY.md)

DDK 承诺在版本之间保持哪些东西可用，哪些不承诺。1.0 之前承诺更弱（见[1.0 之前](#10-之前)）；这一页同时也是 1.0 要冻结的清单，还没定的事项见[冻结清单](#10-冻结清单草案)。

## 版本号

从 1.0 起，本页标为**稳定**的内容遵循[语义化版本](https://semver.org/lang/zh-CN/)：

| 版本 | 可能包含 |
|---|---|
| 补丁版本（`1.2.3` → `1.2.4`） | 只有修复。没有新 API，不改文档写明的默认值 |
| 次版本（`1.2` → `1.3`） | 新增：类型、方法、配置项、规则、starter；弃用；让 DDK 的行为符合文档原本描述的修复 |
| 主版本（`1.x` → `2.0`） | CHANGELOG 里 **Breaking** 下列出的任何变化，每一项都在升级指南里给出迁移步骤 |

要删除的东西，先在某个次版本里标为 `@Deprecated(since = "...", forRemoval = true)`（不是 Java 的，在文档里标为弃用），最早在下一个主版本删除。

## 平台

DDK 运行在什么平台上也属于契约，但并不是每个碰巧能用的版本都算：

| | 1.x 期间 |
|---|---|
| Java | 最低版本在整个 1.x 都是 Java 21，提高它属于主版本。CI 测试的 JDK 见 [SUPPORT.zh-CN.md](./SUPPORT.zh-CN.md#测试过的环境) |
| Spring Boot | 1.x 面向 Spring Boot 4。次版本可以升到更新的 Spring Boot 4.x 次版本，在 CHANGELOG 里列出并附升级说明；补丁版本不改 Spring Boot 的次版本。Spring Boot 5 对应 DDK 2.0 |
| DDK 锁定版本的库（Spring Modulith、MyBatis-Plus、Redisson、ShedLock、RocketMQ 客户端、springdoc、Spring AI、Okio） | 补丁版本只跟进它们的补丁更新，次版本可以跟进次版本更新。会迫使应用改代码的升级属于主版本，只有一个例外：安全修复在需要时随时发布，列在 **Breaking** 下并附迁移说明 |
| DDK API 里的第三方类型 | `GenericRepositoryImpl` 继承 MyBatis-Plus 的 `ServiceImpl`，`RedisJsonMapper.create` 返回 Jackson 的 `ObjectMapper`。DDK 在 1.x 里保留它们，但继承来的方法属于那些库，按上一条的规则随库变化。API 检查只比较 DDK 自己的类，发现不了这类变化 |
| 覆盖 DDK 管理的版本 | 可以，但只有 [SUPPORT.zh-CN.md](./SUPPORT.zh-CN.md#测试过的环境) 列出的版本经过测试；用别的版本由应用自己验证 |
| 中间件和数据库 | SUPPORT 里列出的版本经过测试。其他版本也许可用，但不做承诺 |

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
- **实验性模块：** 目前没有。只经过装配层面测试的模块会列在这里，任何版本都可能改变，对真实系统验证之后移出，`ddk-db-starter` 就是在 0.12 移出的。另一个 `ddk-seata-starter` 在 0.12 弃用，1.0 移除。
- **示例**（`ddk-examples/*`）：参考代码，不是库。
- **日志内容、文档之外的指标标签、异常消息。** 代码要依据异常类型和错误码，而不是消息文本。
- **生成项目的模板。** archetype 的新版本会生成不同的骨架；之前生成的项目不受影响。

## 1.0 冻结清单（草案）

为 1.0 的决定准备的草案，写于 0.10 → 0.11 的演练之后。对上面契约的每一部分，写明 1.0 冻结什么、由什么检查、还有什么没定。1.0 发布之前这里的内容都不是承诺，适用的仍是[1.0 之前](#10-之前)。

### 1.0 冻结

| 契约 | 由什么检查 | 没有覆盖的 |
|---|---|---|
| 稳定类型的 [Java API](#java-api) | 每次改动都用 japicmp 和上一个发布版本比较 | 继承来的库方法（见[平台](#平台)） |
| [配置](#配置)：`ddk.*` 的名称和默认值、`Ddk*AutoConfiguration` 的类名、应用的 Bean 替换 DDK 的 | 各 starter 的装配测试 | 属性改名没有自动检查，靠评审 |
| [数据与传输格式](#数据与传输格式)：事件消息头和消息体、去重消费方的身份、投影和去重两张表、Outbox 的内容、Redis key 和值、缓存失效消息 | [`compat/`](./compat/README.md) 下 0.10.0 以来每个版本的 fixture，每次构建都读取并按同样方式写回；0.10 → 0.11 两个版本同时运行的演练，以及 0.11 → 0.12 的升级与回退演练 | 0.10.0 之前的版本没有 fixture；Outbox 表本身属于 Spring Modulith |
| HTTP：`ApiResponse`、`CommonError` 的错误码和状态码、链路 ID 响应头 | Web 和 tracer starter 的测试 | |
| [架构规则](#架构规则)：已有规则不变严 | 每条规则都有 fixture | |
| [平台](#平台)：最低 Java 21、Spring Boot 4 系列、固定版本的库 | CI 在 Java 21 和 25 上运行 | |

1.0 冻结的是文档写明的行为，不比它更强：

- 事件投递至少一次。`IdempotentConsumer` 跳过消费方身份（`group:consumerId`）和事件 ID 已有记录的消息。这依赖于：两者保持稳定；记录还在去重表的保留期内；记录和处理函数的数据库写入在同一个事务里提交。处理失败时记录随之回滚，记录被清理后同一条消息会再次处理。这个数据库之外的副作用不保证只发生一次。此外还要求事件 ID 跨租户唯一、消息来自消费方信任的来源；租户消息头没有签名。
- `@Idempotent` 在某个 key 的登记存在期间拒绝重复调用。调用失败时撤销自己的登记，可以重试；调用成功后登记随有效期过期，之后同一个 key 会再次执行；执行时间超过有效期的调用可能和重试重叠。不保证副作用只发生一次。
- 聚合锁的可靠程度等于单个 Redis。等锁时被中断是取消（`CancellationException`），不是锁被占用。
- 投影收敛到写模型的当前状态，中间可能短暂显示旧快照。

### 1.0 中保持实验性

| 部分 | 原因 | 什么时候移出 |
|---|---|---|
| [SUPPORT.zh-CN.md](./SUPPORT.zh-CN.md#graalvm-原生镜像) 列为已验证之外的模块的 GraalVM 原生镜像 | CI 以原生镜像运行 `ddk-example-user` 和 `ddk-example-inventory`，覆盖的就是 SUPPORT 列出的模块 | CI 以原生方式覆盖该模块之后 |
| 文档里的指标怎么计算 | 指标名和文档写明的标签是稳定的。事件发布按间隔在后台计数，每次计数仍要扫 `event_publication`，大表上的开销是已知问题 | |
| [不稳定的部分](#不稳定的部分)里的全部内容 | | |

### 1.0 已经决定的

这份草案留下的问题，维护者在 2026-11-10 作了决定。需要的改动放进 0.12，趁次版本还允许破坏性变化；1.0 本身只做冻结。

| 问题 | 决定 | 在哪里落实 |
|---|---|---|
| 什么时候发 1.0 | 0.12 之后，而不是 0.11 之后马上发。0.12 完成下面这些改动；1.0 不再加代码，只做冻结、演练和文档 | 路线图 |
| 被取消的请求的 HTTP 响应 | 单独的错误码：`CommonError.REQUEST_CANCELLED`，返回 503，不再是 500 `SYSTEM_ERROR`。请求本身没有错，可以重试，也不再算作服务端故障 | 0.12，列在 **Breaking** 下 |
| 去重表的主键要不要包含租户 | 不包含，主键保持 `(consumer, message_id)`。「事件 ID 跨租户唯一」和「消息来源可信」仍是 `IdempotentConsumer` 的前提，现在写进了它的 Javadoc 和 README | 0.12，文档 |
| ArchGuard 对层外 `@Configuration` 类的豁免宽度 | 保持不变。新增一条可选规则 `WIRING_CLASSES_ONLY_WIRE`：这类类只能有 `@Bean` 方法和对父类型方法的重写，不能调用 Mapper；生成的项目默认检查 | 0.12 |
| DB 和 Seata starter | `ddk-db-starter` 在 PostgreSQL 和 MySQL 上测试，不再是实验性模块，1.0 和其他 starter 一起冻结。`ddk-seata-starter` 在 0.12 弃用，1.0 既不发布它，也不在 BOM 里列出 | 0.12 弃用 Seata；1.0 移除 |

### 打 1.0 tag 之前

- [x] 上面的问题都已决定，并写进本页
- [x] 0.11.0 已发布，它的 fixture 已加到 `compat/`，`api.baseline.version` 为 0.11.0
- [ ] 0.12.0 完成这些决定需要的改动并发布，它的 fixture 和 API 基线按同样方式跟进
- [ ] 从 0.11 到 1.0 候选版本的升级与回退演练，做法与 0.10 → 0.11 相同
- [ ] 候选版本通过 Java 21 和 25、生成项目、API 比较和原生镜像任务
- [ ] 去掉本节标题里的「草案」，[支持矩阵](./ROADMAP.zh-CN.md#支持状态)与本页一致

## 1.0 之前

0.x 阶段，次版本可能破坏本页的任何内容。每一项破坏都列在 [CHANGELOG](./CHANGELOG.md) 的 **Breaking** 下，迁移步骤见[升级指南](https://domaindrivenkit.com/ddk/upgrade)；API 检查照常运行，所以不会有无意的破坏。补丁版本从不破坏兼容。
