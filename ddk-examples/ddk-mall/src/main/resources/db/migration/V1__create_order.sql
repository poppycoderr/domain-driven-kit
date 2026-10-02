CREATE TABLE t_product
(
    sku_id     VARCHAR(32)    NOT NULL PRIMARY KEY,
    name       VARCHAR(100)   NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL
);

CREATE TABLE t_order
(
    id            BIGINT         NOT NULL PRIMARY KEY,
    customer_id   BIGINT         NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    total_amount  DECIMAL(12, 2) NOT NULL,
    cancel_reason VARCHAR(200),
    version       BIGINT         NOT NULL DEFAULT 0,
    create_by     BIGINT,
    update_by     BIGINT,
    create_time   TIMESTAMP,
    update_time   TIMESTAMP
);

CREATE INDEX idx_order_customer ON t_order (customer_id);

CREATE TABLE t_order_line
(
    id           BIGINT         NOT NULL PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    line_no      INT            NOT NULL,
    sku_id       VARCHAR(32)    NOT NULL,
    product_name VARCHAR(100)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL
);

CREATE INDEX idx_order_line_order ON t_order_line (order_id);
