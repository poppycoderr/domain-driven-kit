# 支持

[English](./SUPPORT.md)

谁在维护 DDK、哪些版本会得到修复、在什么环境下测试过，以及升级之后怎么回退。版本之间保持哪些兼容，见 [COMPATIBILITY.zh-CN.md](./COMPATIBILITY.zh-CN.md)。

## 维护

DDK 由一个人维护：[@poppycoderr](https://github.com/poppycoderr)，AI 编码代理按 [`AGENTS.md`](./AGENTS.md) 的规则参与开发。问题和缺陷请提到 [GitHub Issues](https://github.com/poppycoderr/domain-driven-kit/issues)。尽力答复，但不保证响应时间，也没有商业支持。

## 哪些版本会得到修复

只有最新的次版本线会得到修复，以补丁版本发布。目前是 **0.10.x**。更早的版本线不再修复，需要修复时请升级到最新版本线，按[升级指南](https://domaindrivenkit.com/ddk/upgrade)一次升一个次版本。

## 测试过的环境

每次改动都在 CI 里用下面这些版本跑完整测试。其他版本也许可用，但没有测试过。

| | 测试过的版本 |
|---|---|
| JDK | 21 和 25 |
| Spring Boot | 4.1（基于 4.1.1 构建） |
| 数据库 | H2、MySQL 8.4、PostgreSQL 17 |
| Redis | 7 |
| RocketMQ | 5.5（服务端 5.5.0，客户端 5.5.1） |
| Elasticsearch | 9.4（在 `ddk-mall` 里） |
| DDK 依赖的库 | MyBatis-Plus 3.5.17、Redisson 4.7.0、ShedLock 7.10.1、Spring Modulith 2.1.1、Spring AI 2.0.1、springdoc 3.1.1。Seata 2.6.0 参与编译，但运行时没有测试 |

各模块验证到什么程度、有哪些已知限制，见[支持状态](./ROADMAP.zh-CN.md#支持状态)。标为「仅装配」的模块是实验性的，见 [COMPATIBILITY.zh-CN.md](./COMPATIBILITY.zh-CN.md#不稳定的部分)。虚拟线程下的语义见[虚拟线程](https://domaindrivenkit.com/ddk/core/virtual-threads)一页。

## 升级后回退

升级没有改动持久数据时，回退就是把构建文件里的版本改回去。[升级指南](https://domaindrivenkit.com/ddk/upgrade)的每一节都写明这次升级有没有改动持久数据，有的话怎么回退。DDK 存储或发送的数据：

- 投影的待刷新表和已处理消息表；
- 锁、防重复提交、限流、定时任务锁和缓存的 Redis key；
- 集成事件的消息头。

和升级一样，依赖回退之前，先在副本上演练一遍。
