取消订单时可以填写原因：

- `POST /orders/{id}/cancel` 接受可选的查询参数 `reason`，例如 `POST /orders/42/cancel?reason=买错了`。不传或者只有空白时，视为没有原因。
- 原因去掉首尾空白后最长 200 个字符，超过时返回错误码 `CANCEL_REASON_TOO_LONG`，订单保持原状态。
- 原因要存进数据库。订单的返回结果（下单、查询、取消三个接口）增加字段 `cancelReason`，没有原因时为 `null`。
- 已有的接口和错误码保持不变。

完成后 `mvn verify` 必须通过。
