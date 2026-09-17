# DDK 路线图

[English](./ROADMAP_en.md)

<p align="center">
  <img src="./assets/diagrams/ddk-roadmap.svg" alt="DDK roadmap" />
</p>

这份路线图回答三个问题：DDK 要成为什么、接下来按什么顺序做、怎样让它既好看又好用。每个里程碑都要能独立发布、独立演示，已完成的部分必须有测试兜底。

## 定位

> **DDK 让 Spring Boot 项目里的 DDD 可执行：领域模型开箱即用，架构规则跑在 CI 里，常用中间件按领域语义接入，人和 AI 编码代理都在同一套护栏里写代码。**

三个判断决定了 DDK 做什么、不做什么：

1. **Spring 官方已经覆盖了模块化与事件注册表。** Spring Modulith 2 提供了模块边界校验和持久化的事件发布注册表，jMolecules 提供了 DDD 概念注解。DDK 不重复造这些轮子，而是和它们互通。
2. **国内主流技术栈缺少 DDD 落地层。** MyBatis-Plus、Redis、RocketMQ、Seata、XXL-Job、Nacos 这套组合非常普遍，但几乎没有项目把它们和聚合、仓储、领域事件串成一套约定。这是 DDK 最有差异化的位置。
3. **AI 编码代理需要可执行的架构约束。** Claude Code、Codex 这类工具写代码很快，也很容易把分层写乱。结构可预测、规则能在测试里报错的项目，天然更适合交给代理协作。DDK 已经有 ArchGuard，把它做成代理的反馈回路是顺势而为。

```text
             ┌──────────────────────────── 你的业务代码 ────────────────────────────┐
             │   adapter  ──►  application  ──►  domain  ◄──  infrastructure        │
             └──────┬───────────────┬──────────────┬───────────────┬────────────────┘
                    │               │              │               │
  护栏        ArchGuard 规则 · AGENTS.md / Skills · 生成项目即带架构测试
  领域        ddk-core：聚合 · 值对象 · 领域事件 · 规约 · 仓储契约
  可靠性      Outbox · 幂等 · 分布式锁 · 限流          ← 按聚合 / 命令语义封装
  集成        MyBatis-Plus · Redis · RocketMQ/Kafka · XXL-Job · Elasticsearch · Seata
  AI          Spring AI · MCP：应用服务即工具，受同一套校验与权限约束
  平台        Spring Boot 4 · Jackson 3 · JSpecify · 虚拟线程 · OpenTelemetry
```

### 集成原则

中间件集成最容易把项目做成「大而全」。DDK 只在**领域语义能带来价值**的地方接入：

| 只做薄封装的反例 | DDK 应该提供的 |
|---|---|
| `RedisUtil` 逐个方法转发 `RedisTemplate` | 以聚合 ID 为粒度的 `@AggregateLock`，锁失败映射成业务异常 |
| 给 RocketMQ 再包一层 `send()` | 领域事件经 Outbox 可靠投递到 RocketMQ / Kafka，消费端自带幂等 |
| 把 Elasticsearch 客户端注册成 Bean | 由领域事件驱动的读模型投影，重放可恢复 |
| 再封装一遍 XXL-Job 注解 | 定时任务只调用应用服务，被架构规则约束，不直接碰仓储 |

每个 starter 在合并前都要满足同一份 **Starter 契约**：

- 配置统一在 `ddk.*` 前缀下，有风险的能力默认关闭
- 使用方自定义 Bean 优先（`@ConditionalOnMissingBean`）
- `ApplicationContextRunner` 装配测试；涉及外部中间件的，用 Testcontainers 跑真实集成测试
- 文档站有独立页面：能做什么、不适用的场景、已知问题
- 至少被一个示例或 archetype 使用

## 已完成：基础层

原 Phase 1–5 已全部完成，是后续里程碑的起点：

- **工程基线**：真实 BOM、CI、Spotless、按模块的 JaCoCo 门槛（行 ≥ 70%、分支 ≥ 50%），213 个测试
- **领域模型**：`Identifier`、`ValueObject`、`Entity`、`AggregateRoot`、`DomainEvent`、`Specification`，领域层零框架依赖并由测试保证
- **映射与仓储**：映射器缺失或重复在启动期失败，通用仓储支持乐观锁，H2 集成测试
- **Starter 规范化**：Web、MyBatis、Redis、两级缓存、多数据源、链路追踪、Seata、领域事件、ArchGuard 共 9 个 starter，统一 `ddk.*` 配置
- **骨架与示例**：三层 / 四层 Maven archetype（带生成项目集成测试），可运行的用户注册示例

## v0.1 · 现代基线与首次发布

**目标：别人能在 5 分钟内用上 DDK。** 暂不发布 Maven Central，先把「克隆 → 一条命令安装 → 生成项目」这条路径做顺。

平台升级（Spring Boot 3.4 已于 2025 年 12 月结束开源维护，这是最高优先级）：

- [x] 升级到 Spring Boot 4.1 / Spring Framework 7，适配 Jackson 3 与 Jakarta EE 11
- [x] MyBatis-Plus 切换到 `mybatis-plus-spring-boot4-starter`
- [x] 评估 Seata 对 Boot 4 的支持：Seata 2.6 自动配置只引用 Boot 4 仍保留的 API，DDK 装配测试通过，暂留主线；Seata 运行时尚未在 Boot 4 下验证
- [x] Java 基线保持 21，CI 增加 Java 25 矩阵
- [ ] 公开 API 标注 JSpecify 空安全注解
- [x] 链路追踪 starter 改为基于 `spring-boot-starter-opentelemetry`，不再引入整个 actuator，只保留 traceId 响应头等 DDK 特有能力
- [ ] 使用 Spring Framework 7 自带的 `@Retryable` / `@ConcurrencyLimit`，不再引入额外的重试库

发布就绪：

- [ ] 版本号改为 `0.1.0`，写明 0.x 期间的兼容策略：次版本号可以有破坏性变更，但必须写进发布说明
- [ ] 梳理公开包与内部包，内部实现放进 `internal` 包并由 ArchUnit 约束
- [ ] 公开 API 的 Javadoc、`CHANGELOG.md`、GitHub Release 自动生成发布说明
- [ ] 打 tag 时由 CI 创建 GitHub Release：发布说明、源码包与各模块 jar
- [ ] 提供一条命令完成本地安装与项目生成的脚本，README 快速开始按这条路径编写

## v0.2 · AI 协作

**目标：DDK 生成的项目，交给 Claude Code / Codex 也写不乱。** 这是 DDK 最容易被看见的差异点。

代理护栏：

- [ ] archetype 生成 `AGENTS.md` 与 `CLAUDE.md`：分层职责、命名约定、禁止事项、提交前必须通过的命令
- [ ] 提供 Claude Code Skills / Codex 提示模板：新增聚合、新增用例、新增领域事件、接入 starter
- [ ] ArchGuard 输出机器可读的违规报告（哪条规则、哪个类、怎么改），代理可以据此自我修正
- [ ] 文档站增加「用 AI 代理开发 DDK 项目」专题，附真实对话录屏

AI 能力（基于 Spring AI 2.0）：

- [ ] `ddk-ai-starter`：`ChatClient` 默认配置、结构化输出直接映射为值对象、Token 用量接入 Micrometer
- [ ] `ddk-mcp-starter`：把应用服务的命令 / 查询暴露为 MCP 工具，复用 Bean Validation、权限与审计，禁止直接暴露仓储
- [ ] 示例：让代理通过 MCP 调用用户示例的注册、禁用用例

## v0.3 · 可靠领域事件

**目标：领域事件从「进程内通知」升级为「可靠的集成事件」。**

- [ ] 事务性 Outbox：事件与聚合在同一事务写入，中继器异步投递，失败重试并有死信
- [ ] 投递适配：RocketMQ、Kafka、RabbitMQ
- [ ] 消费端幂等：基于消息 ID 的 Inbox 表，`@IdempotentConsumer`
- [ ] 与 Spring Modulith 事件发布注册表、jMolecules `@Externalized` 互通，不重复实现
- [ ] 事件契约：版本号、Schema 演进规则、按事件类型的序列化配置

## v0.4 · 中间件与类库集成

**目标：覆盖国内主流技术栈里最常用的中间件，全部遵守集成原则与 Starter 契约。**

| 分类 | 集成 | 领域语义 | 优先级 |
|---|---|---|---|
| 并发控制 | Redisson | `@AggregateLock`、按用户 / 租户的限流、`@Idempotent` 防重复提交 | 高 |
| 定时任务 | XXL-Job、ShedLock | 任务只调用应用服务，多实例不重复执行 | 高 |
| 数据库演进 | Flyway | archetype 默认带迁移脚本，示例不再依赖 `schema.sql` | 高 |
| 接口文档 | springdoc-openapi | 自动展开 `ApiResponse<T>` 包装，错误码进入文档 | 高 |
| 读模型 | Elasticsearch | 领域事件驱动的投影与重建 | 中 |
| 横切能力 | MyBatis-Plus 插件 | 多租户、数据权限、审计字段、逻辑删除，统一由操作人上下文驱动 | 中 |
| 配置与注册 | Nacos | 配置刷新对 `ddk.*` 属性生效，保持 starter 可选依赖 | 中 |
| 流量治理 | Sentinel | 应用服务级别的熔断与降级，降级结果映射为业务错误码 | 低 |
| 对象存储 | S3 兼容（MinIO / OSS） | 以值对象表示文件引用，领域层不接触 SDK | 低 |

测试工具：

- [ ] `ddk-test`：聚合断言（产生了哪些事件、不变量是否成立）、Testcontainers 预置（MySQL、Redis、RocketMQ）

## v1.0 · 生产就绪

**目标：可以放心用在生产项目里。**

- [ ] `ddk-mall` 参考应用：订单、库存、支付三个限界上下文，覆盖 Outbox、MQ、缓存、读模型、分布式锁，`docker compose up` 一键启动并带 OpenTelemetry 看板
- [ ] 所有 starter 提供 GraalVM 原生镜像 `RuntimeHints`，示例能构建原生镜像
- [ ] 虚拟线程下的行为验证（锁、ThreadLocal 上下文传播）
- [ ] 公开 API 冻结，此后遵守语义化版本
- [ ] 发布到 Maven Central：等 star 与使用反馈明显增长后再做。届时可使用 GitHub 账号对应的 `io.github.poppycoderr` 命名空间，不依赖自有域名
- [ ] 文档站中英双语

## 贯穿始终：好看又好用

Star 来自两件事：**第一屏就让人看懂价值**，**五分钟内真的跑起来**。这部分不放在某个里程碑里，而是每次发布都要推进。

好看：

- [ ] README 首屏：一句话价值主张、徽章（CI、覆盖率、最新 Release、License）、20 秒动图演示「生成项目 → 违反分层 → 测试报错 → 修复」
- [ ] 与 COLA、Spring Modulith、jMolecules 的对比表，讲清各自适合的场景，而不是贬低别人
- [ ] 仓库社交预览图、GitHub Topics（`ddd`、`spring-boot`、`archunit`、`mybatis-plus`、`ai-agents`）
- [ ] 架构图保持统一视觉，并提供深色模式

好用：

- [ ] 一行命令创建项目：`jbang ddk@poppycoderr new my-app`，支持选择三层 / 四层与需要的 starter
- [ ] 文档站上的在线项目生成器，类似 Spring Initializr
- [ ] 每个 starter 的文档页都有「30 秒上手」代码片段
- [ ] 常见错误有明确报错信息，并在文档里给出排查方法

社区：

- [ ] Issue / PR 模板、`good first issue` 标签、GitHub Discussions
- [ ] 每个版本发布配一篇 codesphere 文章，讲清楚设计取舍
- [ ] 英文 README 与中文 README 同步维护，面向国际社区同时推广

## 明确不做

- 不自研 ORM、消息中间件或注册中心，只做与领域语义相关的集成
- 不重复实现 Spring Modulith 已经提供的能力，优先互通
- 不做事件溯源：领域事件用于集成与解耦，不用于重建状态
- 不做低代码或代码生成平台：生成的只是骨架，业务代码由人（或代理）在护栏内编写
- 不为了追热点堆砌 AI 功能：AI 能力必须经过应用服务，受同样的校验、权限与架构规则约束
