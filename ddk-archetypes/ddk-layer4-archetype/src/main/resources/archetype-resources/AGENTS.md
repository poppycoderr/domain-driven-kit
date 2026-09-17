#set( $h2 = '##' )
#set( $h3 = '###' )
# AGENTS.md

本文件写给在这个项目里工作的 AI 编码代理（Claude Code、Codex 等），人类开发者同样适用。项目由 [Domain Driven Kit](https://github.com/poppycoderr/domain-driven-kit) 的四层架构骨架生成，根包是 `${package}`。

$h2 完成标准

提交前必须通过：

```bash
mvn verify
```

它会运行 `ArchitectureTest`。分层被破坏、领域层引入框架、引用 DDK 的 `internal` 包时构建失败。**不要为了让构建通过而删除或放宽 `ArchitectureTest`**，应当修改代码去满足规则。

$h2 分层与依赖方向

```text
adapter ──► application ──► domain ◄── infrastructure
```

| 包 | 放什么 | 不放什么 |
|---|---|---|
| `adapter.controller` | REST 控制器、`*Request`（带 Bean Validation 注解，提供 `toCommand()`） | 业务判断、仓储调用 |
| `application.command` / `query` | `*Command`（record）、`*Query`（分页查询继承 `PageQuery`） | 框架无关以外的逻辑 |
| `application.response` | `*Response`（record，提供 `static from(聚合)`） | 直接把聚合交给 Jackson |
| `application.service` | 应用服务：取聚合、调用领域方法、保存、转响应；`@Transactional` 在这里 | 业务规则、状态判断 |
| `application.handler` | 领域事件订阅方，`@TransactionalEventListener(phase = AFTER_COMMIT)` | 需要与原事务一起提交的写操作 |
| `domain.model` | 聚合根（继承 `AggregateRoot`）、类型化标识（继承 `Identifier`）、值对象（record 实现 `ValueObject`）、枚举 | Spring、MyBatis、Jackson 的任何类型 |
| `domain.event` | 领域事件（record 实现 `DomainEvent`，过去式命名） | |
| `domain.acl` | 仓储契约（继承 `GenericRepository`）与外部能力端口 | 实现类 |
| `domain.service` | 跨聚合、不属于任何单个聚合的领域逻辑 | 事务、编排 |
| `domain.error` | 错误码枚举（实现 `ErrorCode`） | |
| `infrastructure.acl.impl` | 仓储实现（继承 `GenericRepositoryImpl`）与端口实现 | 业务规则 |
| `infrastructure.converter` | Entity ↔ PO 转换器（`@EnhancedMapper` + `ObjectMapper`，两个方向各一个） | |
| `infrastructure.orm.po` / `mapper` | `*PO` 与 MyBatis-Plus `*Mapper` | |

$h2 编码约定

$h3 领域模型

- 聚合根没有公开构造器和 setter。创建用 `static register(...)` 之类的工厂方法并登记领域事件；从数据库重建用 `static restore(...)`，不登记事件。
- 状态变更与校验写在聚合的方法里（充血模型），应用服务只编排。
- 值对象用 record，在紧凑构造器里校验，非法时抛 `BusinessException(错误码, 参数)`。
- 标识用 `Identifier` 子类（如 `OrderId`），不要在领域方法签名里裸用 `Long`。

$h3 应用层与适配层

- HTTP 入参是 `*Request`，出参是 `*Response`，各自独立成文件，不嵌套在服务类里。
- 路径变量与查询参数显式命名：`@PathVariable("id")`、`@RequestParam(value = "reason", required = false)`。
- 接口统一返回 `ApiResponse<T>`；业务失败抛 `BusinessException`，由全局异常处理器转换，不要在控制器里 try-catch。

$h3 基础设施层

- 单表 CRUD 用 `BaseMapper` 与 lambda wrapper，只有 wrapper 表达不了的 SQL 才写 XML。
- `update(null, lambdaUpdate())` 不会触发自动填充，需要显式设置更新时间。
- Entity 与 PO 结构不同，转换器手写，不用反射拷贝。

$h3 通用

- 注释只写类级别（这个类做什么）；方法注释只留给有陷阱的逻辑（并发、顺序、算法）。不写逐行注释和 TODO 占位。
- 单行不超过约 150 列；record 每个组件单独一行。
- 不要引用 `com.ddk..internal..` 包里的类型，它们不属于 DDK 公开 API。

$h2 可用的 Skills

`.claude/skills/` 下有按本项目约定编写的步骤说明，支持 Skills 的代理可直接调用，其他代理可以当作检查清单阅读：

- `ddk-add-aggregate`：新增聚合（标识、聚合根、仓储、PO、Mapper、转换器、表结构）
- `ddk-add-use-case`：新增用例（Request、Command、Response、应用服务方法、接口）
- `ddk-add-domain-event`：新增领域事件与订阅方

$h2 参考

完整可运行的示例见 DDK 仓库的 [`ddk-examples/ddk-example-user`](https://github.com/poppycoderr/domain-driven-kit/tree/main/ddk-examples/ddk-example-user)。
