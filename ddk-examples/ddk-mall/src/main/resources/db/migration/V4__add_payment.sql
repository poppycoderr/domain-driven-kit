ALTER TABLE t_order
    ADD COLUMN expires_at TIMESTAMP NULL;

CREATE INDEX idx_order_status_expires ON t_order (status, expires_at);

CREATE TABLE t_payment
(
    id               BIGINT         NOT NULL PRIMARY KEY,
    order_id         BIGINT         NOT NULL,
    customer_id      BIGINT         NOT NULL,
    amount           DECIMAL(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    expires_at       TIMESTAMP      NULL,
    channel_trade_no VARCHAR(64),
    paid_at          TIMESTAMP      NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    create_time      TIMESTAMP      NULL,
    update_time      TIMESTAMP      NULL,
    CONSTRAINT uk_payment_order UNIQUE (order_id)
);
