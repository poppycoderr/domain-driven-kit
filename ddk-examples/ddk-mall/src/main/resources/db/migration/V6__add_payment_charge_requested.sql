-- 请求渠道扣款的时间。先于扣款单独提交，之后的结算如果没有完成，对账任务凭它找到支付单，向渠道查询结果
ALTER TABLE t_payment
    ADD COLUMN charge_requested_at TIMESTAMP NULL;

CREATE INDEX idx_payment_reconciliation ON t_payment (status, charge_requested_at);
