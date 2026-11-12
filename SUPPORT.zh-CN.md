# 支持

[English](./SUPPORT.md)

谁在维护 DDK、哪些版本会得到修复、在什么环境下测试过，以及升级之后怎么回退。版本之间保持哪些兼容，见 [COMPATIBILITY.zh-CN.md](./COMPATIBILITY.zh-CN.md)。

## 维护

DDK 由一个人维护：[@poppycoderr](https://github.com/poppycoderr)，AI 编码代理按 [`AGENTS.md`](./AGENTS.md) 的规则参与开发。问题和缺陷请提到 [GitHub Issues](https://github.com/poppycoderr/domain-driven-kit/issues)。尽力答复，但不保证响应时间，也没有商业支持。安全问题请按 [SECURITY.zh-CN.md](./SECURITY.zh-CN.md) 私下报告。

## 哪些版本会得到修复

只有最新的次版本线会得到修复，以补丁版本发布。目前是 **0.12.x**。更早的版本线不再修复，需要修复时请升级到最新版本线，按[升级指南](https://domaindrivenkit.com/ddk/upgrade)一次升一个次版本。

## 测试过的环境

每次改动都在 CI 里用下面这些版本跑完整测试。其他版本也许可用，但没有测试过。

| | 测试过的版本 |
|---|---|
| JDK | 21 和 25 |
| Spring Boot | 4.1（基于 4.1.2 构建） |
| 数据库 | H2、MySQL 8.4、PostgreSQL 17 |
| Redis | 7 |
| RocketMQ | 5.5（服务端 5.5.0，客户端 5.5.1） |
| Elasticsearch | 9.4（在 `ddk-mall` 里） |
| DDK 依赖的库 | MyBatis-Plus 3.5.18.1、Redisson 4.7.0、ShedLock 7.10.1、Spring Modulith 2.1.2、Spring AI 2.0.2、springdoc 3.1.1 |

各模块验证到什么程度、有哪些已知限制，见[支持状态](./ROADMAP.zh-CN.md#支持状态)。标为「仅装配」的模块是实验性的，见 [COMPATIBILITY.zh-CN.md](./COMPATIBILITY.zh-CN.md#不稳定的部分)。虚拟线程下的语义见[虚拟线程](https://domaindrivenkit.com/ddk/core/virtual-threads)一页。

## GraalVM 原生镜像

原生镜像按模块逐个验证，不对整个 DDK 一次性承诺。CI 用 GraalVM CE 25.0.2 编译 `ddk-example-user`（Spring Boot 父 pom 提供的 `mvn -Pnative native:compile`），再对可执行文件运行 `scripts/native-smoke.sh`。它检查示例的成功路径和下面列出的错误路径：一次条件查询加雪花 ID 插入、按 ID 读取、按两种条件分页并核对总数、修改、禁用、删除、Bean Validation（`VALIDATION_ERROR`）、业务错误、领域规则、数据不存在、一次 MCP 工具调用，以及事务提交后的两个领域事件监听器。它不检查乐观锁冲突、事务回滚，也不涉及示例没有用到的功能，例如租户、数据权限、Outbox、Redis。

CI 还会在一个 Redis 服务上编译 `ddk-example-inventory`，运行 `scripts/native-smoke-inventory.sh`：经过两级缓存的读取以及写入后的缓存失效、在聚合锁和防重复提交下的一次预留以及对重复请求的拒绝、领域规则和 Bean Validation、触发限流、提交后刷新的投影，以及一个经 Spring Modulith 的 JDBC 发布登记、配合 `IdempotentConsumer` 运行的监听器。它只运行一个实例，所以不检查跨实例的锁和缓存失效、等锁超时、失败刷新或发布的重试，也不涉及租户。

| 模块 | 原生镜像 |
|---|---|
| `ddk-core`、`ddk-mybatis`、`ddk-mybatis-starter`、`ddk-web-starter` | CI 已验证 |
| `ddk-event-starter` | CI 验证了应用内的领域事件，以及经 Spring Modulith 的 JDBC 发布登记、配合 `IdempotentConsumer` 运行的监听器。把事件外发到 RocketMQ 没有验证 |
| `ddk-concurrency-starter` | CI 已在 Redis 上验证：聚合锁、防重复提交、限流。key 表达式读取的参数类型会自动登记反射 |
| `ddk-redis-starter`、`ddk-cache-starter` | CI 已验证：两级缓存的读取、失效，以及发出的失效消息。应用存进 Redis 的类型由应用自己登记反射，例如用 `@RegisterReflectionForBinding` |
| `ddk-projection-starter` | CI 已验证：标记随事务写入、提交后刷新，在 H2 上 |
| `ddk-mcp-starter` | CI 已验证：同步服务端、streamable HTTP、工具调用 |
| archetype | 用 0.10.0 生成的项目手动编译并启动过一次，不在 CI 里 |
| 其他模块 | 未验证。也许可用，但没有测试覆盖 |

`ddk-mybatis-starter` 自动登记 MyBatis-Plus 和默认 ID 生成器在原生镜像里需要的反射，`ddk-cache-starter` 登记一级缓存用到的 Caffeine 类，`ddk-concurrency-starter` 登记 key 表达式读取的参数，应用不用为此做任何事。CI 覆盖到某个模块之后，它才会在上表里升级。

## 升级后回退

升级没有改动持久数据时，回退就是把构建文件里的版本改回去。[升级指南](https://domaindrivenkit.com/ddk/upgrade)的每一节都写明这次升级有没有改动持久数据，有的话怎么回退。DDK 存储或发送的数据见[数据与传输格式](./COMPATIBILITY.zh-CN.md#数据与传输格式)。

和升级一样，依赖回退之前，先在副本上演练一遍。
