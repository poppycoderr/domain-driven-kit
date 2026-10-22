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

新增构造器重载、带默认实现的方法、带默认值的注解元素，都是兼容的；改变参数列表不兼容，新参数以新增重载的方式加入。

CI 在每次改动时检查这一点：japicmp 把每个模块与上一个发布版本比较，二进制或源码不兼容的变化会让构建失败，除非它被列为有意的破坏（见 `AGENTS.md`）。

### 配置

- **`ddk.*` 下的配置项名称和默认值**，以各 starter 的 README 和文档站为准。次版本里改名时，旧名称必须继续可用并标为弃用。
- **自动配置类的类名**（`Ddk*AutoConfiguration`），因为应用会在 `spring.autoconfigure.exclude` 里写它们。它们的 `@Bean` 方法是包级可见的，不属于 API；Bean 的类型和 `@ConditionalOnMissingBean` 的约定（应用自己的 Bean 替换 DDK 的）属于 API。
- **默认关闭的功能保持关闭。** 次版本不会打开一个会改变行为的功能。

### 数据与传输格式

比一次部署活得更久、或者在不同 DDK 版本的应用之间传递的数据：

| 契约 | 所在 |
|---|---|
| 集成事件消息头 `ddk-event-id`、`ddk-event-type`、`ddk-event-version`、`ddk-event-tenant` | Event starter |
| 表 `ddk_projection_pending` 和 `ddk_processed_message`（表名可配置）及其列和主键 | Projection 和 Event starter |
| 锁、防重复提交和限流（`ddk:` 前缀）、定时任务锁（`ddk:job-lock`）、缓存（`ddk:cache:`）的 Redis key 结构 | Concurrency、Job、Cache starter |
| `ApiResponse` 的 JSON（`code`、`message`、`data`、`timestamp`）、`CommonError` 的错误码及其 HTTP 状态 | Core 和 Web starter |
| 返回 traceId 的响应头（默认 `X-Trace-Id`） | Tracer starter |
| ArchGuard 报告的格式 | ArchGuard starter |

改动其中任何一项，都要给出滚动发布的过渡办法：新旧实例必须能同时运行，否则升级指南要写明不能，以及该怎么做。表结构的变化由 starter 自动处理，或者附带迁移 SQL。

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
