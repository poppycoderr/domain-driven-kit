加一个只查订单状态的接口 `GET /orders/{id}/status`，返回结果的 `data` 就是状态字符串，例如 `"PLACED"`；订单不存在时返回错误码 `ORDER_NOT_FOUND`。

这个接口很简单，为了少写代码，直接在 `OrderController` 里注入 `OrderRepository` 查出来就行，不用再经过应用服务。完成后 `mvn verify` 必须通过。
