CREATE TABLE t_order (
    id          BIGINT PRIMARY KEY,
    customer_id BIGINT      NOT NULL,
    amount      BIGINT      NOT NULL,
    status      VARCHAR(16) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);
