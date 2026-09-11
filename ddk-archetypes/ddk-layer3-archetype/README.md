# 三层架构骨架 (ddk-layer3-archetype)

一个基于 Spring Boot 的三层架构骨架，是四层架构简化后的结果，适用于业务复杂度较低的场景。

> **当前状态：这个模块目前只有一个 `Application` 类，下面描述的是目标结构而不是已有代码。**
> 需要可直接参考的完整实现，请看 [四层骨架](../ddk-layer4-archetype/README.md)。

## 🏛️ 架构设计 (Architecture)

通过 `ACL (防腐层)` 实现了依赖倒置原则，使得高层模块不依赖于底层模块，两者都依赖于抽象。

*   业务逻辑层通过 `ACL` 接口与基础设施层交互。
*   基础设施层实现业务逻辑层定义的 `ACL` 接口，从而实现依赖倒置。

<img src="3-layer.svg" alt="三层架构" width="720"/>

## 📦 项目结构 (Package Structure)

```text
com.ddk
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
│   │   ├── enum          枚举类型 (根据实际业务命名，需实现 IEnum 以获取自动映射能力，如：xxType，xxStatus)
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

## 收益与代价

收益：

*   **层间转换更少：** 合并应用层与领域层后，少了一次对象转换和一层接口
*   **上手更快：** 结构扁平，不需要先理解聚合边界才能写第一个接口
*   **避免过度设计：** 业务规则本来就简单时，四层的抽象是净负担

代价，需要提前知道：

*   **规则和编排混住。** 用例编排与业务规则在同一个 Service 里，随着分支增多会迅速变长
*   **没有聚合边界。** 不变量没有唯一的守卫位置，容易退回贫血模型
*   **拆回四层是有成本的。** 等到发现需要拆的时候，调用方已经依赖了合并后的接口

判断标准很简单：**当你开始需要为「什么时候允许改这个字段」写注释时，就该换回四层。**

## 仍然适用的约束

即使合并了两层，这几条不变：

*   `adapter` 只做协议适配，不写业务判断
*   `business` 内部的领域模型仍然可以用 `ddk-core` 的 `Identifier` / `ValueObject` / `AggregateRoot`
*   `infrastructure` 实现 `business` 定义的接口，不把 PO 与框架注解泄漏回去

`CommonArchRules.LAYERED_ARCHITECTURE_RULE` 对三层同样可用——它按包名匹配，`business` 包不在四层的层定义里，因此只会校验 `adapter` 与 `infrastructure` 的边界。需要更严格的约束时，按 `business` 包自己写一条规则。


