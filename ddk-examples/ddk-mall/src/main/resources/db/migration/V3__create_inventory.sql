CREATE TABLE t_stock
(
    sku_id      VARCHAR(32) NOT NULL PRIMARY KEY,
    on_hand     INT         NOT NULL,
    reserved    INT         NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE t_stock_reservation
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    order_id    BIGINT      NOT NULL,
    sku_id      VARCHAR(32) NOT NULL,
    quantity    INT         NOT NULL,
    status      VARCHAR(20) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    CONSTRAINT uk_reservation_order_sku UNIQUE (order_id, sku_id)
);

INSERT INTO t_stock (sku_id, on_hand, reserved)
VALUES ('SKU-KEYBOARD', 10, 0),
       ('SKU-MOUSE', 50, 0),
       ('SKU-MONITOR', 3, 0);
