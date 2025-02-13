# 🎉 项目名称 (ddk-layer3-archetype)

一个基于 Spring Boot 的三层架构示例项目。 该项目是基于经典的四层架构进行简化后的结果，更适用于业务复杂度较低的场景。

## 🏛️ 架构设计 (Architecture)

采用三层架构，它是对经典四层架构的简化，通过合并领域层和应用层，从而减少不必要的抽象，降低复杂度，更适合处理业务逻辑相对简单的场景。

*   **Adapter Layer (适配层/表示层):** 用户交互和外部系统入口 🚪
*   **Business Layer (业务逻辑层):** 核心业务逻辑、用例编排和事务管理 🧠
*   **Infrastructure Layer (基础设施层):** 技术实现和外部依赖 🛠️

## 📦 包结构 (Package Structure)

```text
io.github.ddk
├── adapter (适配层/表示层)
│   ├── common          通用的适配器组件，例如类型转换、通用处理等
│   ├── web             Web 相关的适配器
│   │   ├── controller    REST 控制器，处理 HTTP 请求
│   │   ├── config        Web 配置，例如 Spring MVC 配置
│   │   └── exception     Web 相关的异常处理
│   └── mq              消息事件相关的适配器
│       └── consumer      消息消费者
├── business (业务逻辑层)
│   ├── service         业务服务，处理用例和复杂业务逻辑
│   ├── command         命令对象，封装业务逻辑层的操作 (适用于更新相关的操作 xxxCommand)
│   ├── query           查询对象，封装业务逻辑层的查询 (适用于查询相关的操作 xxxQuery)
│   ├── event           业务事件，用于层之间的解耦
│   ├── handler         事件处理器，用于处理业务事件
│   ├── response        中间传输对象 （xxxDTO）
│   ├── model           领域模型
│   │   ├── entity        实体 (无需后缀)
│   │   ├── valueobject   值对象 (无需后缀)
│   │   ├── aggregate     聚合根 (xxxAggregate)
│   │   ├── enum          枚举类型 (根据实际业务命名，需继承 BaseEnum 以获取自动映射能力，如：xxType，xxStatus)
│   └── acl             防腐层接口，定义与基础设施层的交互接口 (通常是涉及到外部调用的接口 xxxGateway，仓储接口 xxxRepository)
├── infrastructure (基础设施层)
│   ├── acl             业务逻辑层声明接口的实现类
│   │   └── impl        acl接口的实现类 (xxxGatewayImpl， xxxRepositoryImpl)
│   ├── rabbitmq        RabbitMQ 相关配置实现
│   ├── remote          与外部服务进行远程调用
│   │   ├── client        远程服务客户端 (通常是 xxxClient)
│   │   ├── config        远程服务配置
│   │   ├── request       远程服务请求对象 (xxxRequest)
│   │   └── response      远程服务响应对象 (可复用业务逻辑层 DTO)
│   ├── redis           Redis 相关配置实现
│   ├── orm             ORM 相关的实现
│   │   ├── po            Persistent Object，数据库持久化对象 (xxxPO)
│   │   ├── mapper        ORM 映射器，例如 MyBatis Mapper (xxxMapper)
│   │   └── config        ORM 配置，例如 MyBatis 配置
└── util                通用工具类 (xxxUtils)
```

*   **adapter:** 负责处理与外部系统的交互，如 Web 请求、消息队列消息等。
*   **business:** 包含核心业务逻辑、用例编排和事务管理。是原四层架构中应用层与领域层的合并。
*   **infrastructure:** 提供技术支持，如数据库访问、消息传递、缓存等。

## Benefits of Simplification (简化带来的好处) ➕

*   **减少了复杂性:** 通过合并应用层和领域层，减少了层与层之间的交互和转换，降低了代码的复杂性。 📉
*   **更快的开发速度:** 减少了不必要的抽象，可以更快地实现业务功能。 ⏱️
*   **更容易理解和维护:** 代码结构更加扁平化，更易于理解和维护。 📚
*   **更适合简单业务场景:** 对于业务逻辑相对简单的项目，三层架构已经足够满足需求，避免了过度设计。 👍

## 🛡️ 依赖倒置原则 (Dependency Inversion Principle)

本项目通过 `ACL (防腐层)` 实现了依赖倒置原则，使得高层模块不依赖于底层模块，两者都依赖于抽象。

*   业务逻辑层通过 `ACL` 接口与基础设施层交互。
*   基础设施层实现业务逻辑层定义的 `ACL` 接口，从而实现依赖倒置。

<img src="3-layer.png" alt="4-layer" width=500/>
