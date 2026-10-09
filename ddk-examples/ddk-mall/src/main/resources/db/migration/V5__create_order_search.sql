CREATE TABLE t_order_search
(
    order_id      BIGINT         NOT NULL PRIMARY KEY,
    customer_id   BIGINT         NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    total_amount  DECIMAL(12, 2) NOT NULL,
    item_count    INT            NOT NULL,
    product_names VARCHAR(500)   NOT NULL,
    expires_at    TIMESTAMP      NULL,
    refreshed_at  TIMESTAMP      NULL
);

CREATE INDEX idx_order_search_customer ON t_order_search (customer_id, status);
