---
name: ddk-add-use-case
description: Add a use case (an API operation) to this DDK four-layer service - request, command or query, response, application service method and controller endpoint. Use when the user asks for a new endpoint or a new operation on an existing aggregate, such as "cancel an order" or "list products by category".
---
#set( $h2 = '##' )

# 新增用例

以「取消订单」为例，根包 `${package}`。

$h2 1. 规则先落在领域层

用例背后的业务规则写成聚合方法，例如 `Order.cancel(String reason)`：检查当前状态是否允许取消，不允许时抛 `BusinessException`，允许时修改状态并登记 `OrderCancelledEvent`。聚合里已有对应方法时直接复用。

$h2 2. 应用层

| 文件 | 要点 |
|---|---|
| `application/command/CancelOrderCommand.java` | record，只含用例需要的数据，不带校验注解 |
| `application/query/*Query.java` | 读用例使用；分页查询继承 `PageQuery` |
| `application/response/OrderResponse.java` | record，提供 `static from(Order)`；敏感字段在这里脱敏 |
| `application/service/OrderService.java` | 写用例加 `@Transactional`：`find` 取聚合（不存在抛 `NOT_FOUND` 错误码）→ 调用领域方法 → `update` 保存 → `OrderResponse.from(...)`。这里不写 if 业务判断 |

$h2 3. 适配层

| 文件 | 要点 |
|---|---|
| `adapter/controller/request/CancelOrderRequest.java` | record，带 Bean Validation 注解，提供 `toCommand()` |
| `adapter/controller/OrderController.java` | `@Valid @RequestBody`，路径变量写成 `@PathVariable("id")`，查询参数写成 `@RequestParam(value = "...", required = ...)`；返回 `ApiResponse<T>`，无数据时 `ApiResponse.ofSuccess()` |

控制器只调用应用服务，不注入仓储，不捕获 `BusinessException`。

$h2 4. 测试与验证

- 领域方法的规则用单元测试覆盖。
- 接口层用 `@SpringBootTest` + `@AutoConfigureMockMvc` 覆盖参数绑定、校验失败返回 400、业务错误码。
- 运行 `mvn verify`，`ArchitectureTest` 必须通过。
