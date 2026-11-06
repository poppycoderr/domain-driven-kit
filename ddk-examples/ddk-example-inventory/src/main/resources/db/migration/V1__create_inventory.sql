CREATE TABLE t_sku (
    id        BIGINT      NOT NULL PRIMARY KEY,
    name      VARCHAR(64) NOT NULL,
    available INT         NOT NULL,
    version   BIGINT      NOT NULL
);

CREATE TABLE t_low_stock (
    sku_id    BIGINT      NOT NULL PRIMARY KEY,
    name      VARCHAR(64) NOT NULL,
    available INT         NOT NULL
);

CREATE TABLE t_reservation_journal (
    reservation_id VARCHAR(64) NOT NULL PRIMARY KEY,
    sku_id         BIGINT      NOT NULL,
    quantity       INT         NOT NULL
);

INSERT INTO t_sku (id, name, available, version) VALUES (1, 'apple', 10, 0), (2, 'pear', 8, 0);
