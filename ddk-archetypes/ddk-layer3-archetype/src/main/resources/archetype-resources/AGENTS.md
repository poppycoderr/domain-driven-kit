#set( $h2 = '##' )
#set( $h3 = '###' )
# AGENTS.md

本文件写给在这个项目里工作的 AI 编码代理（Claude Code、Codex 等），人类开发者同样适用。项目由 [Domain Driven Kit](https://github.com/poppycoderr/domain-driven-kit) 的三层架构骨架生成，根包是 `${package}`。

$h2 完成标准

提交前必须通过：

```bash
mvn verify
```

它会运行 `ArchitectureTest`。分层被破坏、引用 DDK 的 `internal` 包时构建失败。**不要为了让构建通过而删除或放宽 `ArchitectureTest`**，应当修改代码去满足规则。

$h2 分层与依赖方向

```text
adapter ──► business ◄── infrastructure
```

三层结构把应用层与领域层合并为 `business`。业务层可以使用 Spring（例如 `@Service`、`@Transactional`），但不得引用基础设施层的实现类，只依赖自己在 `business.acl` 中定义的接口。

| 包 | 放什么 | 不放什么 |
|---|---|---|
| `adapter.controller` | REST 控制器、`*Request`（带 Bean Validation 注解，提供 `toCommand()`） | 业务判断、仓储调用 |
| `business.command` / `query` | `*Command`（record）、`*Query`（分页查询继承 `PageQuery`） | |
| `business.response` | `*Response`（record，提供 `static from(实体)`） | 直接把实体交给 Jackson |
| `business.service` | 业务服务：用例编排与事务 | 可以写进实体方法的状态判断 |
| `business.model` | 实体、值对象（record）、枚举，可继承 DDK 领域模型基类 | PO、Mapper |
| `business.event` / `handler` | 业务事件（record 实现 `DomainEvent`）与订阅方（`AFTER_COMMIT`） | |
| `business.acl` | 仓储契约（继承 `GenericRepository`）与外部能力接口 `*Gateway` | 实现类 |
| `business.error` | 错误码枚举（实现 `ErrorCode`） | |
| `infrastructure.acl.impl` | 仓储实现（继承 `GenericRepositoryImpl`）与接口实现 | 业务规则 |
| `infrastructure.converter` | 实体 ↔ PO 转换器（`@EnhancedMapper` + `ObjectMapper`，两个方向各一个） | |
| `infrastructure.orm.po` / `mapper` | `*PO` 与 MyBatis-Plus `*Mapper` | |

$h2 编码约定

$h3 业务模型

- 状态变更与校验优先写在实体方法里，避免业务服务变成一堆 setter 调用。
- 值对象用 record，在紧凑构造器里校验，非法时抛 `BusinessException(错误码, 参数)`。
- 实体没有公开 setter；创建与从数据库重建分别用不同的静态工厂方法，重建时不登记事件。

$h3 接口与持久化

- HTTP 入参是 `*Request`，出参是 `*Response`，各自独立成文件。
- 路径变量与查询参数显式命名：`@PathVariable("id")`、`@RequestParam(value = "status", required = false)`。
- 接口统一返回 `ApiResponse<T>`；业务失败抛 `BusinessException`，不要在控制器里 try-catch。
- 单表 CRUD 用 `BaseMapper` 与 lambda wrapper；`update(null, lambdaUpdate())` 不会触发自动填充，需要显式设置更新时间。

$h3 通用

- 注释只写类级别；方法注释只留给有陷阱的逻辑。不写逐行注释和 TODO 占位。
- 单行不超过约 150 列；record 每个组件单独一行。
- 不要引用 `com.ddk..internal..` 包里的类型，它们不属于 DDK 公开 API。

$h2 可用的 Skills

- `ddk-add-feature`（位于 `.claude/skills/`）：新增一个业务功能，从实体、仓储、持久化到接口的完整步骤。支持 Skills 的代理可直接调用，其他代理可以当作检查清单阅读。
