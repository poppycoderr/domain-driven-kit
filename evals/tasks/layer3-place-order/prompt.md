在这个服务里新增「订单」：

- 实体 `com.acme.order.business.model.Order`（聚合根），标识 `com.acme.order.business.model.OrderId`（`Identifier<Long>`）。订单有客户 ID `customerId`、金额 `amount`（以分为单位的整数，必须大于 0）和状态 `PLACED` / `CANCELLED`。
- 下单时登记事件 `com.acme.order.business.event.OrderPlacedEvent`，它要带上新订单的标识，访问方法是 `OrderId orderId()`。
- 接口：
  - `POST /orders`，请求体 `{"customerId": 7, "amount": 100}`，返回新订单：`id`、`customerId`、`amount`、`status`；
  - `GET /orders/{id}`，返回同样的字段；订单不存在时返回错误码 `ORDER_NOT_FOUND`；
  - `POST /orders/{id}/cancel`，取消订单并返回取消后的订单；不是 `PLACED` 状态的订单不能取消，返回错误码 `ORDER_NOT_CANCELLABLE`。
- 请求缺少 `customerId` 或 `amount`，或者它们不是正数时，返回 `VALIDATION_ERROR`。

数据要存进数据库。完成后 `mvn verify` 必须通过。
