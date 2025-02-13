# 🎉 项目名称 (ddk-layer4-archetype)

一个基于 Spring Boot 的四层架构示例项目，旨在展示如何使用领域驱动设计 (DDD) 构建可维护、可测试和可扩展的应用程序。

## 🏛️ 架构设计 (Architecture)

经典的四层架构：

*   **Adapter Layer (适配层/表示层):** 用户交互和外部系统入口 🚪
*   **Application Layer (应用层):** 逻辑编排和事务管理 ⚙️
*   **Domain Layer (领域层):** 核心业务逻辑和领域模型 🧠
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
├── application (应用层)
│   ├── service         应用服务，处理用例
│   ├── command         命令对象，封装应用层的操作 （适用于更新相关的操作 xxxCommand）
│   ├── query           查询对象，封装应用层的查询 （适用于查询相关的操作 xxxQuery）
│   ├── event           应用事件，用于层之间的解耦
│   ├── handler         事件处理器，用于处理应用事件
│   ├── response        应用层返回 DTO （中间传输对象，基础设施层可复用 xxxDTO）
│   └── acl             防腐层接口，定义与基础设施层的交互接口 （通常是涉及到外部调用的接口 xxxGateway）
├── domain (领域层)
│   ├── model           领域模型
│   │   ├── entity        实体 （无需后缀）
│   │   ├── valueobject   值对象 （无需后缀）
│   │   ├── aggregate     聚合根 （xxxAggregate）
│   │   ├── enum          枚举类型 （根据实际业务命名，需继承 BaseEnum 以获取自动映射能力，如：xxType，xxStatus）
│   │   └── dto           领域层内部使用的数据传输对象（适用于实体构造时的中间参数组装类）
│   ├── service         领域服务，处理复杂的领域逻辑（通常涉及到跨实体的逻辑）
│   ├── event           领域事件，表示领域内发生的事件 （xxxEvent）
│   ├── exception       领域特定的异常 （参考 AbstractException及其子类）
│   └── acl             防腐层接口，定义与基础设施层的交互接口（通常是仓储接口 xxxRepository）
├── infrastructure (基础设施层)
│   ├── acl             应用层与领域层声明接口的实现类
│   │   └── impl        acl接口的实现类 （xxxGatewayImpl， xxxRepositoryImpl）
│   ├── rabbitmq        RabbitMQ 相关配置实现
│   ├── remote          与外部服务进行远程调用
│   │   ├── client        远程服务客户端 （通常是 xxxClient）
│   │   ├── config        远程服务配置
│   │   ├── request       远程服务请求对象 （xxxRequest）
│   │   └── response      远程服务响应对象 （可复用应用层 DTO）
│   ├── redis           Redis 相关配置实现
│   ├── orm             ORM 相关的实现
│   │   ├── po            Persistent Object，数据库持久化对象 （xxxPO）
│   │   ├── mapper        ORM 映射器，例如 MyBatis Mapper （xxxMapper）
│   │   └── config        ORM 配置，例如 MyBatis 配置
└── util                通用工具类 （xxxUtils）
```

*   **adapter:** 负责处理与外部系统的交互，如 Web 请求、消息队列消息等。
*   **application:** 协调领域对象来完成特定的用例。
*   **domain:** 包含核心业务逻辑和领域知识。
*   **infrastructure:** 提供技术支持，如数据库访问、消息传递、缓存等。

## 🛡️ 依赖倒置原则 (Dependency Inversion Principle)

通过 `ACL (防腐层)` 实现了依赖倒置原则，使得高层模块不依赖于底层模块，两者都依赖于抽象。

*   应用层通过 `ACL` 接口与基础设施层交互。
*   领域层通过 `ACL` 接口（Repository）与基础设施层交互。
*   基础设施层实现应用层和领域层定义的 `ACL` 接口，从而实现依赖倒置。

<img src="4-layer.png" alt="4-layer" width=500/>