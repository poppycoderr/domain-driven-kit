# 四层 DDD 示例：用户注册 (ddk-example-user)

一个可以直接运行的四层架构应用：内存 H2 数据库、启动即带种子数据，演示 DDK 的领域模型、通用仓储、领域事件、全局异常处理与架构守卫如何在一个真实用例里配合。

## 运行

```bash
mvn -pl ddk-examples/ddk-example-user -am install -DskipTests
mvn -pl ddk-examples/ddk-example-user spring-boot:run
```

应用启动在 `http://localhost:8080`，种子数据里有 `alice`、`bobby`、`carol` 三个用户，密码都是 `password123`。

## 冒烟命令

```bash
# 注册
curl -s -X POST localhost:8080/users -H 'Content-Type: application/json' \
  -d '{"username":"dave01","password":"password123","gender":1,"phoneNumber":"13900139001"}'

# 查询（手机号返回脱敏值）
curl -s localhost:8080/users/1001

# 分页：启用中的女性用户
curl -s -X POST localhost:8080/users/page -H 'Content-Type: application/json' \
  -d '{"enabled":true,"genders":[1],"pageNum":1,"pageSize":10}'

# 修改资料，未传的字段不变
curl -s -X PUT localhost:8080/users/1001 -H 'Content-Type: application/json' -d '{"email":"alice@corp.example"}'

# 禁用 / 启用
curl -s -X PATCH 'localhost:8080/users/1001/disable?reason=spam'
curl -s -X PATCH localhost:8080/users/1001/enable

# 删除
curl -s -X DELETE localhost:8080/users/1002

# 错误响应：用户名重复 / 手机号非法 / 用户不存在，均为 400 + 领域错误码
curl -s -X POST localhost:8080/users -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123","gender":1,"phoneNumber":"13900139002"}'
```

## 通过 MCP 调用

示例同时引入了 `ddk-mcp-starter`，把注册、查询、禁用三个用例暴露为 MCP 工具，端点是 `http://localhost:8080/mcp`（streamable HTTP）。在 Claude Code 里接入：

```bash
claude mcp add --transport http ddk-example-user http://localhost:8080/mcp
```

| 工具 | 对应用例 |
|---|---|
| `register_user` | `UserService.register` |
| `get_user` | `UserService.get` |
| `disable_user` | `UserService.disable` |

`adapter.mcp.UserMcpTools` 与 `UserController` 同属适配层：参数带 Bean Validation 注解，只调用应用服务。用户名重复时工具返回 `USERNAME_TAKEN: ...`，参数不合法时返回 `VALIDATION_ERROR: username: ...`，模型可以据此调整后重试。端到端行为由 `UserMcpToolsTest` 通过 MCP 客户端验证。

## 请求如何穿过四层

```text
POST /users
  UserController.register               adapter      校验 RegisterUserRequest，转成命令
    UserService.register                application  事务边界：检查用户名、编排领域对象
      User.register                     domain       守卫不变量，登记 UserRegisteredEvent
      UserRepository.create             domain 端口
        UserRepositoryImpl              infrastructure  User -> UserPO，写 t_user，发布领域事件
    UserResponse.from(user)             application  手机号脱敏
  UserEventHandler.on(UserRegisteredEvent)            事务提交后执行
```

## 每个类演示的取舍

| 类 | 取舍 |
|---|---|
| `domain.model.entity.User` | 聚合根没有公开构造器和 setter；`register` 登记事件，`restore` 不登记；`disable` 幂等 |
| `domain.model.entity.UserId` | 类型化标识，参数传反在编译期报错 |
| `domain.model.valueobject.PhoneNumber` / `Email` | 值对象用 record，构造期校验，违反时抛带领域错误码的 `BusinessException`，接口返回 400 而不是 500 |
| `domain.model.enums.Gender` | 编码与枚举的转换写在枚举自身的 `of` 里 |
| `domain.acl.UserRepository` / `UserIdGenerator` | 端口定义在领域层，实现在基础设施层 |
| `application.service.UserService` | 只做编排与事务，业务规则在聚合里 |
| `application.response.UserResponse` | `from(User)` 转换并脱敏，领域对象不直接交给 Jackson |
| `application.handler.UserEventHandler` | `@TransactionalEventListener(AFTER_COMMIT)`，事务回滚时不发通知 |
| `adapter.controller.request.*Request` | HTTP 校验放在请求对象上，`toCommand()` 转成应用层命令 |
| `infrastructure.converter.*` | Entity ↔ PO 手写转换，两个方向各注册一次 |
| `infrastructure.orm.po.UserPO` | `IdType.INPUT` 使用领域预生成的标识，`@Version` 对应聚合版本号 |
| `infrastructure.security.BCryptPasswordEncryptionService` | 领域服务的技术实现 |
| `ArchitectureTest` | 分层规则写成测试，违反即构建失败 |

## 测试

| 测试 | 覆盖 |
|---|---|
| `UserTest` | 聚合不变量、事件登记、幂等禁用，纯单元测试 |
| `UserApiTest` | 通过 HTTP 走完注册、修改、禁用、启用、删除的完整生命周期，以及参数绑定、分页条件、错误码与领域事件 |
| `ArchitectureTest` | 四层依赖方向与领域层框架无关 |
