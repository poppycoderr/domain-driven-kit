<p align="center">
    <img src="./logo.png" alt="ddk logo" width="220" />
</p>

<h1 align="center">Domain Driven Kit</h1>

<p align="center">
    一个面向 Java / Spring Boot 的 DDD 脚手架与工程约定工具集。
</p>

<p align="center">
    <a href="README_en.md">English</a> ·
    <a href="https://poppycoder.netlify.app/#/docs/ddk/">文档站</a> ·
    <a href="./ROADMAP.md">开发计划</a>
</p>

---

## 项目定位

`domain-driven-kit` 不是一个“大而全”的业务框架，而是一个持续演进的 **Java DDD 工程化工具箱**。它把分层架构、通用响应、异常体系、分页、对象映射、仓储抽象、Spring Boot starter 和架构守卫沉淀成可复用代码，帮助团队把 DDD 从概念落到项目结构和代码约束上。

适合这些场景：

- 新项目想快速建立清晰的 DDD / 分层架构骨架
- 团队需要统一 Controller、Application、Domain、Infrastructure 的职责边界
- 希望把异常、响应、分页、仓储、对象映射这类重复代码标准化
- 希望用 ArchUnit 把架构规则放进测试和 CI，而不是只写在文档里

## 当前状态

这是一个个人维护中的开源项目，目标是持续补齐工程基线、示例和 starter 能力。目前仓库更适合学习、参考和本地试用，暂未发布到 Maven Central。

| 模块 | 当前能力 | 状态 |
|---|---|---|
| `ddk-core` | `ApiResponse`、异常体系、分页对象、Mapper 抽象、仓储接口 | 可用，持续补测试 |
| `ddk-mybatis` | MyBatis-Plus 通用仓储、查询条件解析、分页适配 | 可用，已补分页排序传递 |
| `ddk-web-starter` | Jackson、CORS、全局异常处理 | 可用，已修复全局异常处理未生效问题 |
| `ddk-archguard-starter` | DDD 分层 ArchUnit 规则 | 可用，已收紧 Domain -> Infrastructure 依赖 |
| `ddk-db-starter` | 多数据源动态注册 | 可试用 |
| `ddk-cache-starter` | 缓存 starter 草案 | 待重写 |
| `ddk-archetypes` | 三层 / 四层示例骨架 | 待改造成真正 Maven archetype |
| `ddk-examples` | 示例工程入口 | 待补完整可运行示例 |

## 最近更新

- 修复 `BaseExceptionHandler` 缺少 `@RestControllerAdvice` 导致全局异常处理不生效的问题
- 为 `PageQuery.pageSize` 增加默认最大值，避免外部传入超大分页参数
- 修复 `PageQuery.addSort()` 添加的排序未传递到 MyBatis-Plus 查询的问题
- 收紧 `CommonArchRules`，避免领域层依赖基础设施层
- 重写 README 与文档计划，明确当前状态、已知限制和下一步路线

## 模块结构

```text
domain-driven-kit
├── ddk-dependencies      依赖管理模块，计划补成真实 BOM
├── ddk-core              核心抽象：异常、响应、分页、映射、仓储接口
├── ddk-mybatis           MyBatis-Plus 仓储实现与查询适配
├── ddk-starters          Spring Boot starter 集合
│   ├── ddk-web-starter
│   ├── ddk-mybatis-starter
│   ├── ddk-redis-starter
│   ├── ddk-cache-starter
│   ├── ddk-db-starter
│   ├── ddk-tracer-starter
│   ├── ddk-seata-starter
│   └── ddk-archguard-starter
├── ddk-archetypes        三层 / 四层项目骨架
└── ddk-examples          示例工程
```

## 快速开始

环境要求：

- JDK 21
- Maven 3.9+
- Spring Boot 3.4.x

本地构建：

```bash
git clone https://github.com/poppycoderr/domain-driven-kit.git
cd domain-driven-kit
mvn -B -ntp verify
mvn -B install
```

在业务项目中引入：

```xml
<properties>
    <ddk.version>1.0.0-SNAPSHOT</ddk.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-core</artifactId>
        <version>${ddk.version}</version>
    </dependency>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-mybatis</artifactId>
        <version>${ddk.version}</version>
    </dependency>
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-web-starter</artifactId>
        <version>${ddk.version}</version>
    </dependency>
</dependencies>
```

更多完整示例见：[快速开始](https://poppycoder.netlify.app/#/docs/ddk/quickstart)。

## 推荐分层

```text
adapter          -> application -> domain
infrastructure  --------------------^
```

核心约束：

- `adapter` 只做协议适配，不写业务规则
- `application` 负责编排用例、事务和领域对象调用
- `domain` 保存业务规则，不依赖 Spring、MyBatis、Jackson 等框架
- `infrastructure` 实现领域层定义的仓储和外部依赖接口

可以通过 `ddk-archguard-starter` 把这些约束放进 ArchUnit 测试。

## 开发计划

短期优先级：

1. 补齐 `ddk-core` 和 `ddk-mybatis` 的单元测试
2. 把 `ddk-dependencies` 补成真实 BOM
3. 实现 `Entity` / `ValueObject` / `AggregateRoot` / `DomainEvent` 等领域模型基类
4. 规范化 starter 配置前缀、配置元数据和装配测试
5. 补一个完整可运行的 `ddk-examples` 示例

完整路线图见 [ROADMAP.md](./ROADMAP.md)。

## 文档

- [DDK 文档首页](https://poppycoder.netlify.app/#/docs/ddk/)
- [快速开始](https://poppycoder.netlify.app/#/docs/ddk/quickstart)
- [分层约定与架构守卫](https://poppycoder.netlify.app/#/docs/ddk/conventions)
- [开发与重构计划](https://poppycoder.netlify.app/#/docs/ddk/contributing)

## 贡献

欢迎提交 issue 或 PR。当前项目仍在持续完善中，优先接受这几类贡献：

- 修复 starter 装配和配置问题
- 补测试和最小可运行示例
- 改进 DDD 分层示例和文档
- 对已有设计提出更清晰的工程取舍
